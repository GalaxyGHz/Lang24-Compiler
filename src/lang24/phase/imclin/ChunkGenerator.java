package lang24.phase.imclin;

import java.util.*;

import org.stringtemplate.v4.compiler.CodeGenerator.conditional_return;

import lang24.common.report.*;
import lang24.data.ast.tree.*;
import lang24.data.ast.tree.defn.*;
import lang24.data.ast.tree.defn.AstFunDefn.AstParDefn;
import lang24.data.ast.tree.defn.AstFunDefn.AstRefParDefn;
import lang24.data.ast.tree.expr.*;
import lang24.data.ast.tree.stmt.*;
import lang24.data.ast.tree.type.AstRecType.AstCmpDefn;
import lang24.data.ast.tree.type.*;
import lang24.data.ast.visitor.*;
import lang24.data.imc.code.ImcInstr;
import lang24.data.imc.code.expr.*;
import lang24.data.imc.code.stmt.*;
import lang24.data.imc.visitor.ImcVisitor;
import lang24.data.lin.LinCodeChunk;
import lang24.data.lin.LinDataChunk;
import lang24.data.mem.*;
import lang24.data.type.*;
import lang24.data.type.visitor.*;
import lang24.phase.imcgen.ImcGen;
import lang24.phase.memory.Memory;
import lang24.phase.seman.SemAn;

public class ChunkGenerator implements AstFullVisitor<Object, Object>{

    private Vector<ImcStmt> linearStatements;
    private HashMap<MemLabel, Vector<ImcStmt>> blocks;

    public ChunkGenerator() {
        linearStatements = new Vector<>();
        blocks = new HashMap<>();
    }

    private Vector<ImcStmt> optimizeFunctionBlocks(MemFrame frame, MemLabel entryLabel, MemLabel exitLabel) {
        // Add missing jumps and labels
        Vector<ImcStmt> newFunctionBlock = addJumpsAndLabels();

        // Break function block into smaller blocks
        HashMap<MemLabel, Vector<ImcStmt>> blocks = breakBlocksUp(newFunctionBlock);

        // Throw out unreachable blocks
        removeUnreachableBlocks(entryLabel, exitLabel, blocks);

        // Remove empty blocks of type (LABEL(x), JUMP(y)) by changing every branch to x with a branch to y
        // removeEmptyBlocks(entryLabel, blocks);

        // Combine cjumps with their false statements
        extendCJUMPs(blocks);

        // Combine the blocks in a greedy fashion
        newFunctionBlock = putBlocksBackTogether(entryLabel, blocks);
        
        return newFunctionBlock;
    }

    private Vector<ImcStmt> addJumpsAndLabels() { // Adds a jump in front of every Label that doesnt already have a jump
        Vector<ImcStmt> newFunctionBlock = new Vector<>();
        newFunctionBlock.add(linearStatements.get(0));

        for (int i = 1; i < linearStatements.size(); i++) {
            if (linearStatements.get(i) instanceof ImcLABEL && !(linearStatements.get(i - 1) instanceof ImcJUMP || linearStatements.get(i - 1) instanceof ImcCJUMP)) {
                // Add jumps before lables, excluding the first one
                newFunctionBlock.add(new ImcJUMP(((ImcLABEL) linearStatements.get(i)).label));
                newFunctionBlock.add(linearStatements.get(i));
            } 
            else if ((linearStatements.get(i) instanceof ImcJUMP || linearStatements.get(i) instanceof ImcCJUMP) && i < linearStatements.size() - 1 && !(linearStatements.get(i + 1) instanceof ImcLABEL)) {
                // Add labels after jumps, except for the last jump
                newFunctionBlock.add(linearStatements.get(i));
                newFunctionBlock.add(new ImcLABEL(new MemLabel()));
            } 
            else 
                newFunctionBlock.add(linearStatements.get(i));
        }
        return newFunctionBlock;
    }

    private HashMap<MemLabel, Vector<ImcStmt>> breakBlocksUp(Vector<ImcStmt> newFunctionBlock) {
        // Break the function block into smaller blocks that start with a label and end with a jump or cjump
        HashMap<MemLabel, Vector<ImcStmt>> blocks = new HashMap<>();

        int labelIndex = 0;
        for (int i = 0; i < newFunctionBlock.size(); i++) {
            if (newFunctionBlock.get(i) instanceof ImcJUMP || newFunctionBlock.get(i) instanceof ImcCJUMP) {
                Vector<ImcStmt> block = new Vector<>(newFunctionBlock.subList(labelIndex, i + 1));
                blocks.put(((ImcLABEL) newFunctionBlock.get(labelIndex)).label, block);
                labelIndex = i + 1;
            }
        }
        return blocks;
    }

    private void removeUnreachableBlocks(MemLabel entryLabel, MemLabel exitLabel, HashMap<MemLabel, Vector<ImcStmt>> blocks) {
        HashSet<MemLabel> reachableLabels = new HashSet<>();

        Stack<MemLabel> labelsToCheck = new Stack<>();
        labelsToCheck.add(entryLabel);
        while (!labelsToCheck.isEmpty()) {
            MemLabel label = labelsToCheck.pop();

            if (label.equals(exitLabel) || reachableLabels.contains(label))
                continue;
            reachableLabels.add(label);

            Vector<ImcStmt> block = blocks.get(label);
            if (block.lastElement() instanceof ImcJUMP) {
                labelsToCheck.push(((ImcJUMP) block.lastElement()).label);
            }
            if (block.lastElement() instanceof ImcCJUMP) {
                labelsToCheck.push(((ImcCJUMP) block.lastElement()).posLabel);
                labelsToCheck.push(((ImcCJUMP) block.lastElement()).negLabel);
            }
        }

        HashSet<MemLabel> nonReachableLabels = new HashSet<MemLabel>(blocks.keySet());
        nonReachableLabels.removeAll(reachableLabels);
        for (MemLabel nonReachableLabel : nonReachableLabels) {
            blocks.remove(nonReachableLabel);
        }
    }

    private MemLabel newLabelFromDeletedBlocks(MemLabel label, HashMap<MemLabel, MemLabel> emptyBlockLabels) {
        MemLabel result = label;
        while (emptyBlockLabels.containsKey(result)) result = emptyBlockLabels.get(result);
        return result;
    }

    private void removeEmptyBlocks(MemLabel entryLabel, HashMap<MemLabel, Vector<ImcStmt>> blocks) {
        HashMap<MemLabel, MemLabel> emptyBlockLabels = new HashMap<>();

        for (MemLabel label : blocks.keySet()) {
            if (blocks.get(label).size() == 2) {
                Vector<ImcStmt> emptyBlock = blocks.get(label);
                if (emptyBlock.get(1) instanceof ImcJUMP && ((ImcLABEL) emptyBlock.get(0)).label != entryLabel) {
                    MemLabel startLabel = ((ImcLABEL) emptyBlock.get(0)).label;
                    MemLabel endLabel = ((ImcJUMP) emptyBlock.get(1)).label;
                    emptyBlockLabels.put(startLabel, endLabel);
                }
            }
        }

        for (MemLabel startLabel : emptyBlockLabels.keySet()) {
            blocks.remove(startLabel);
        }

        for (MemLabel label : blocks.keySet()) {
            Vector<ImcStmt> block = blocks.get(label);
            if (block.lastElement() instanceof ImcJUMP) {
                ImcJUMP jump = (ImcJUMP) block.lastElement();
                if (emptyBlockLabels.containsKey(jump.label)) {
                    MemLabel newLabel = newLabelFromDeletedBlocks(jump.label, emptyBlockLabels);
                    block.removeLast();
                    block.add(new ImcJUMP(newLabel));
                }
            } else if (block.lastElement() instanceof ImcCJUMP) {
                ImcCJUMP cjump = (ImcCJUMP) block.lastElement();
                if (emptyBlockLabels.containsKey(cjump.posLabel)) {
                    MemLabel newLabel = newLabelFromDeletedBlocks(cjump.posLabel, emptyBlockLabels);
                    block.removeLast();
                    cjump = new ImcCJUMP(cjump.cond, newLabel, cjump.negLabel);
                    block.add(cjump);
                }
                if (emptyBlockLabels.containsKey(cjump.negLabel)) {
                    MemLabel newLabel = newLabelFromDeletedBlocks(cjump.negLabel, emptyBlockLabels);
                    block.removeLast();
                    cjump = new ImcCJUMP(cjump.cond, cjump.posLabel, newLabel);
                    block.add(cjump);
                }
            } else
                throw new Report.InternalError();
        }
    }

    private void extendCJUMPs(HashMap<MemLabel, Vector<ImcStmt>> blocks) {
        Vector<MemLabel> blocksWithCjumps = new Vector<>();

        for (MemLabel label : blocks.keySet()) {
            if (blocks.get(label).lastElement() instanceof ImcCJUMP)
                blocksWithCjumps.add(label);
        }

        while (!blocksWithCjumps.isEmpty()) {
            MemLabel label = blocksWithCjumps.get(0);
            Vector<ImcStmt> cjump = blocks.get(label);
            MemLabel negativeLabel = ((ImcCJUMP) cjump.lastElement()).negLabel;
            Vector<ImcStmt> negativeBody = blocks.get(negativeLabel);
            if (negativeBody == null) {
                cjump.add(new ImcJUMP(negativeLabel));
                blocksWithCjumps.remove(label);
                continue;
            }
            blocks.remove(label);
            blocks.remove(negativeLabel);

            cjump.addAll(negativeBody);
            blocks.put(label, cjump);

            if (!(negativeBody.lastElement() instanceof ImcCJUMP))
                blocksWithCjumps.remove(label);
            else 
                blocksWithCjumps.remove(negativeLabel);
        }

    }

    private Vector<ImcStmt> putBlocksBackTogether(MemLabel entryLabel, HashMap<MemLabel, Vector<ImcStmt>> blocks) {
        Vector<ImcStmt> finalBlock = new Vector<>();

        Vector<MemLabel> labels = new Vector<MemLabel>(blocks.keySet());
        Collections.sort(labels, (a, b) -> a.name.compareTo(b.name));

        MemLabel targetLabel = entryLabel;
        while (!labels.isEmpty()) {
            if (!labels.contains(targetLabel)) {
                targetLabel = labels.iterator().next();
            }
                Vector<ImcStmt> block = blocks.get(targetLabel);
                if (!finalBlock.isEmpty() && ((ImcJUMP) finalBlock.lastElement()).label.equals(((ImcLABEL) block.firstElement()).label))
                    finalBlock.removeLast();
                finalBlock.addAll(block);
                labels.remove(targetLabel);
                targetLabel = ((ImcJUMP) block.lastElement()).label;
        }
        return finalBlock;
    }


    @Override
	public Object visit(AstFunDefn funDefn, Object arg) {
        MemFrame frame = Memory.frames.get(funDefn);
        if (frame == null) return null;

        MemLabel entryLabel = ImcGen.entryLabel.get(funDefn);
        MemLabel exitLabel = ImcGen.exitLabel.get(funDefn);

        ImcGen.stmtImc.get(funDefn.stmt).accept(new ImcLinearizer(), null);
        // LinCodeChunk code = new LinCodeChunk(frame, linearStatements, entryLabel, exitLabel);
        // ImcLin.addCodeChunk(code);

        Vector<ImcStmt> blocks = optimizeFunctionBlocks(frame, entryLabel, exitLabel);
        LinCodeChunk code = new LinCodeChunk(frame, blocks, entryLabel, exitLabel);
        ImcLin.addCodeChunk(code);
        linearStatements.clear();
        
		if (funDefn.pars != null)
			funDefn.pars.accept(this, arg);
		if (funDefn.stmt != null)
			funDefn.stmt.accept(this, arg);
		if (funDefn.defns != null)
			funDefn.defns.accept(this, arg);
		funDefn.type.accept(this, arg);
		return null;
	}

    @Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
        SemType myType = SemAn.ofType.get(atomExpr).actualType();
        if (atomExpr.type == AstAtomExpr.Type.STR) {
            MemAbsAccess absAccess = Memory.strings.get(atomExpr);
            ImcLin.addDataChunk(new LinDataChunk(absAccess));
        }
		return null;
	}

    @Override
	public Object visit(AstVarDefn varDefn, Object arg) {
        MemAccess access = Memory.varAccesses.get(varDefn);
        if (access instanceof MemAbsAccess) {
            MemAbsAccess absAccess = (MemAbsAccess) access;
            ImcLin.addDataChunk(new LinDataChunk(absAccess));
        }
        varDefn.type.accept(this, arg);
        return null;
	}

    private class ImcLinearizer implements ImcVisitor<ImcInstr, Object> {

        private boolean argumentConflict(ImcExpr expr) {
            if (expr instanceof ImcCALL) {
                return true;
            } else if (expr instanceof ImcBINOP) {
                boolean fst = argumentConflict(((ImcBINOP) expr).fstExpr);
                boolean snd = argumentConflict(((ImcBINOP) expr).sndExpr);
                return fst || snd;
            } else if (expr instanceof ImcCONST) {
                return false;
            } else if (expr instanceof ImcMEM) {
                return true;
            } else if (expr instanceof ImcNAME) {
                return false;
            } else if (expr instanceof ImcSEXPR) {
                // boolean stmt = argumentConflict(((ImcSEXPR) expr).stmt);
                boolean exp = argumentConflict(((ImcSEXPR) expr).expr);
                return /*stmt ||*/ exp;
            } else if (expr instanceof ImcTEMP) {
                return false;
            } else if (expr instanceof ImcUNOP) {
                return argumentConflict(((ImcUNOP) expr).subExpr);
            } else {
                throw new Report.InternalError();
            }
        }


        public ImcInstr visit(ImcBINOP binOp, Object arg) {
            ImcExpr fst = (ImcExpr) binOp.fstExpr.accept(this, null);
            ImcExpr snd = (ImcExpr) binOp.sndExpr.accept(this, null);
            return new ImcBINOP(binOp.oper, fst, snd);
        }
    
        public ImcInstr visit(ImcCALL call, Object arg) {
            Vector<ImcExpr> newArgs = new Vector<>();
            for (ImcExpr expr : call.args) {
                if (argumentConflict(expr)) { // If it is call or mem
                    expr = (ImcExpr) expr.accept(this, true);
                    ImcTEMP tmp = new ImcTEMP(new MemTemp());
                    ImcMOVE move = new ImcMOVE(tmp, expr);
                    linearStatements.add(move);
                    newArgs.add(tmp);
                    
                } else {
                    newArgs.add(expr);
                }
            }
            // CALL can be only directly on the right side of MOVE or inside an EXPRESSION STATEMENT
            ImcCALL newCALL = new ImcCALL(call.label, call.offs, newArgs);
            if (arg == null) { // move call outside of expression and change with register
                ImcTEMP tmp = new ImcTEMP(new MemTemp());
                ImcMOVE move = new ImcMOVE(tmp, newCALL);
                linearStatements.add(move);
                return tmp;
            }
            return newCALL; // its inside an EXPRESSION STATEMENT so leave it be
            
        }
    
        public ImcInstr visit(ImcCJUMP cjump, Object arg) {
            ImcExpr cond = (ImcExpr) cjump.cond.accept(this, null);
            return new ImcCJUMP(cond, cjump.posLabel, cjump.negLabel);
        }
    
        public ImcInstr visit(ImcCONST constant, Object arg) {
            return constant;
        }
    
        public ImcInstr visit(ImcESTMT eStmt, Object arg) {
            ImcExpr ex = (ImcExpr) eStmt.expr.accept(this, true);
            return new ImcESTMT(ex);
        }
    
        public ImcInstr visit(ImcJUMP jump, Object arg) {
            return jump;
        }
    
        public ImcInstr visit(ImcLABEL label, Object arg) {
            return label;
        }
    
        public ImcInstr visit(ImcMEM mem, Object arg) {
            ImcExpr addr = (ImcExpr) mem.addr.accept(this, null);
            return new ImcMEM(addr);
        }
    
        public ImcInstr visit(ImcMOVE move, Object arg) {
            ImcExpr dst = (ImcExpr) move.dst.accept(this, null);
            ImcExpr src;
            if (move.dst instanceof ImcTEMP)
                src = (ImcExpr) move.src.accept(this, true);
            else
                src = (ImcExpr) move.src.accept(this, null);
            return new ImcMOVE(dst, src);
        }
    
        public ImcInstr visit(ImcNAME name, Object arg) {
            return name;
        }
    
        public ImcInstr visit(ImcSEXPR sExpr, Object arg) {
            ImcStmt stmt = (ImcStmt) sExpr.stmt.accept(this, null);
            ImcExpr exp = (ImcExpr) sExpr.expr.accept(this, null);
            return new ImcSEXPR(stmt, exp);
        }
    
        public ImcInstr visit(ImcSTMTS stmts, Object arg) {
            for (ImcStmt stmt : stmts.stmts) {
                ImcStmt newStmt = (ImcStmt) stmt.accept(this, null);
                if (newStmt != null)
                    linearStatements.add(newStmt);
            }
            return null;
        }
    
        public ImcInstr visit(ImcTEMP temp, Object arg) {
            return temp;
        }
    
        public ImcInstr visit(ImcUNOP unOp, Object arg) {
            ImcExpr subExp = (ImcExpr) unOp.subExpr.accept(this, null);
            return new ImcUNOP(unOp.oper, subExp);
        }
    }
    
}
