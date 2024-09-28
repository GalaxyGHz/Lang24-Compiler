package lang24.phase.imcgen;

import java.util.*;
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
import lang24.data.imc.code.expr.*;
import lang24.data.imc.code.stmt.*;
import lang24.data.mem.*;
import lang24.data.type.*;
import lang24.data.type.visitor.*;
import lang24.phase.memory.MemEvaluator;
import lang24.phase.memory.Memory;
import lang24.phase.seman.SemAn;
import lang24.phase.seman.TypeResolver;

/**
 * Transforming into intermediate code.
 */
public class ImcGenerator implements AstFullVisitor<Object, Object> {

    // HELPERS

    Stack<MemFrame> framePointers = new Stack<>();
    Stack<MemLabel> exitPoints = new Stack<>();

    @Override
	public Object visit(AstFunDefn funDefn, Object arg) {
        if (funDefn.stmt == null) { // If it is a prototype of a function, so no body, dont do anything
            if (funDefn.pars != null)
                funDefn.pars.accept(this, null);
            funDefn.type.accept(this, null);
            return null;
        }

        MemFrame frame = Memory.frames.get(funDefn);
        framePointers.push(frame);

        MemLabel entryLabel = new MemLabel();
        MemLabel exitLabel = new MemLabel();
        ImcGen.entryLabel.put(funDefn, entryLabel);
        ImcGen.exitLabel.put(funDefn, exitLabel);
        exitPoints.push(exitLabel);

		if (funDefn.pars != null)
			funDefn.pars.accept(this, null);
        if (funDefn.stmt != null)
			funDefn.stmt.accept(this, null);
        if (funDefn.defns != null)
			funDefn.defns.accept(this, funDefn);
		funDefn.type.accept(this, null);

        // Add entry label to start
        ImcStmt entry = new ImcLABEL(entryLabel);
        ImcStmt statement = ImcGen.stmtImc.get(funDefn.stmt);

        Vector<ImcStmt> addedEntryLabel = new Vector<>();
        addedEntryLabel.add(entry);
        addedEntryLabel.add(statement);
        addedEntryLabel.add(new ImcMOVE(new ImcTEMP(frame.RV), new ImcCONST(42)));
        addedEntryLabel.add(new ImcJUMP(exitLabel));
        ImcStmt newSTMTS = new ImcSTMTS(addedEntryLabel);
        ImcGen.stmtImc.put(funDefn.stmt, newSTMTS);

        exitPoints.pop();
        framePointers.pop();
		return null;
	}

    private long stringToChar(String string) {
        if (string.equals("'\\''")) {
            return 39L;
        } else if (string.equals("'\\n'")) {
            return 10L;
        } else if (string.length() == 5) {
            return Long.parseLong(string.substring(2, string.length() - 1), 16);
        } else {
            return (long) string.charAt(1);
        }
    }

    // IGNORE THESE

    @Override
	public Object visit(AstTypDefn typDefn, Object arg) {
		// typDefn.type.accept(this, arg);
		return null;
	}

    @Override
	public Object visit(AstVarDefn varDefn, Object arg) {
		// varDefn.type.accept(this, arg);
		return null;
	}

    @Override
	public Object visit(AstFunDefn.AstRefParDefn refParDefn, Object arg) {
		// refParDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AstFunDefn.AstValParDefn valParDefn, Object arg) {
		// valParDefn.type.accept(this, arg);
		return null;
	}

    @Override
	public Object visit(AstRecType.AstCmpDefn cmpDefn, Object arg) {
		// cmpDefn.type.accept(this, arg);
		return null;
	}

    @Override
	public Object visit(AstArrType arrType, Object arg) {
		// arrType.elemType.accept(this, arg);
		// arrType.size.accept(this, arg);
		return null;
	}

    // EXPRESSIONS

    @Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
        long value = 0L;
        ImcExpr constant = null;
        switch (atomExpr.type) {
            case AstAtomExpr.Type.VOID:
                value = 42L;
                constant = new ImcCONST(value);
                ImcGen.exprImc.put(atomExpr, constant);
                break;

            case AstAtomExpr.Type.PTR:
                value = 0L;
                constant = new ImcCONST(value);
                ImcGen.exprImc.put(atomExpr, constant);
                break;

            case AstAtomExpr.Type.BOOL:
                if (atomExpr.value.equals("false")) {
                    value = 0L;
                    constant = new ImcCONST(value);
                    ImcGen.exprImc.put(atomExpr, constant);
                } else {
                    value = 1L;
                    constant = new ImcCONST(value);
                    ImcGen.exprImc.put(atomExpr, constant);
                }
                break;

            case AstAtomExpr.Type.CHAR:
                value = stringToChar(atomExpr.value);
                constant = new ImcCONST(value);
                ImcGen.exprImc.put(atomExpr, constant);
                break;

            case AstAtomExpr.Type.INT:
                try {
                    value = Long.valueOf(atomExpr.value);
                } catch (Exception e) {
                    throw new Report.Error(atomExpr.location(), "Memory error: Given integer is too big! It must be in the interval [-2^63, 2^63 - 1].");
                }
                constant = new ImcCONST(value);
                ImcGen.exprImc.put(atomExpr, constant);
                break;

            case AstAtomExpr.Type.STR:
                MemAccess access = Memory.strings.get(atomExpr);
                ImcExpr name = new ImcNAME(((MemAbsAccess) access).label);
                ImcGen.exprImc.put(atomExpr, name);
                break;

            default:
                throw new InternalError("How did you get here? ImcGenerator, AstAtomExpr.");
        }
		return null;
	}

    @Override
	public Object visit(AstPfxExpr pfxExpr, Object arg) {
        boolean returnAddress = AstPfxExpr.Oper.PTR == pfxExpr.oper;
		pfxExpr.expr.accept(this, returnAddress);
        ImcExpr subExpr = ImcGen.exprImc.get(pfxExpr.expr);
        ImcExpr unOp = null;
        switch (pfxExpr.oper) {
            case AstPfxExpr.Oper.ADD:
                ImcGen.exprImc.put(pfxExpr, subExpr);
                break;
            case AstPfxExpr.Oper.SUB:
                unOp = new ImcUNOP(ImcUNOP.Oper.NEG, subExpr);
                ImcGen.exprImc.put(pfxExpr, unOp);
                break;
            case AstPfxExpr.Oper.NOT:
                unOp = new ImcUNOP(ImcUNOP.Oper.NOT, subExpr);
                ImcGen.exprImc.put(pfxExpr, unOp);
                break;
            case AstPfxExpr.Oper.PTR:
                ImcGen.exprImc.put(pfxExpr, ((ImcMEM) subExpr).addr);
                break;

            default:
                throw new InternalError("How did you get here? ImcGenerator, AstPfxExpr.");
        }
		return null;
	}

    @Override
	public Object visit(AstBinExpr binExpr, Object arg) {
		binExpr.fstExpr.accept(this, arg);
		binExpr.sndExpr.accept(this, arg);
        ImcExpr fstExpr = ImcGen.exprImc.get(binExpr.fstExpr);
        ImcExpr sndExpr = ImcGen.exprImc.get(binExpr.sndExpr);
        ImcExpr binOp = null;
        switch (binExpr.oper) {
            case AstBinExpr.Oper.OR:
                binOp = new ImcBINOP(ImcBINOP.Oper.OR, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.AND:
                binOp = new ImcBINOP(ImcBINOP.Oper.AND, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.EQU:
                binOp = new ImcBINOP(ImcBINOP.Oper.EQU, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.NEQ:
                binOp = new ImcBINOP(ImcBINOP.Oper.NEQ, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.LTH:
                binOp = new ImcBINOP(ImcBINOP.Oper.LTH, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.GTH:
                binOp = new ImcBINOP(ImcBINOP.Oper.GTH, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.LEQ:
                binOp = new ImcBINOP(ImcBINOP.Oper.LEQ, fstExpr, sndExpr);
                    ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.GEQ:
                binOp = new ImcBINOP(ImcBINOP.Oper.GEQ, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.ADD:
                binOp = new ImcBINOP(ImcBINOP.Oper.ADD, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.SUB:
                binOp = new ImcBINOP(ImcBINOP.Oper.SUB, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.MUL:
                binOp = new ImcBINOP(ImcBINOP.Oper.MUL, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.DIV:
                binOp = new ImcBINOP(ImcBINOP.Oper.DIV, fstExpr, sndExpr);
                    ImcGen.exprImc.put(binExpr, binOp);
                break;
            case AstBinExpr.Oper.MOD:
                binOp = new ImcBINOP(ImcBINOP.Oper.MOD, fstExpr, sndExpr);
                ImcGen.exprImc.put(binExpr, binOp);
                break;
        
            default:
                throw new InternalError("How did you get here? ImcGenerator, AstPfxExpr.");

        }
		return null;
	}

    @Override
	public Object visit(AstNameExpr nameExpr, Object arg) {
        AstDefn defn = SemAn.definedAt.get(nameExpr);
        MemAccess access = (defn instanceof AstVarDefn) ? (Memory.varAccesses.get((AstVarDefn) defn)) : (Memory.parAccesses.get((AstParDefn) defn));

        ImcExpr result = null;

        if (access instanceof MemAbsAccess) {
            MemAbsAccess absAccesss = (MemAbsAccess) access;
            result = new ImcNAME(absAccesss.label);
        } else if (access instanceof MemRelAccess) {
            MemRelAccess relAccesss = (MemRelAccess) access;
            long currentFrameDepth = framePointers.peek().depth;
            long targetDepth = relAccesss.depth;

            result = new ImcTEMP(framePointers.peek().FP);
            while (targetDepth != currentFrameDepth) {
                result = new ImcMEM(result);
                currentFrameDepth -= 1;
            }
            ImcCONST offset = new ImcCONST(relAccesss.offset);
            result = new ImcBINOP(ImcBINOP.Oper.ADD, result, offset);
            if (defn instanceof AstRefParDefn) {
                result = new ImcMEM(result);
            }

        } else 
            throw new InternalError("How did you get here? ImcGenerator, nameExpr.");

        result = new ImcMEM(result);

        ImcGen.exprImc.put(nameExpr, result);
        
        return null;
	}

    @Override
	public Object visit(AstSfxExpr sfxExpr, Object arg) {
		sfxExpr.expr.accept(this, arg);
        switch (sfxExpr.oper) {
            case AstSfxExpr.Oper.PTR:
                ImcExpr expr = ImcGen.exprImc.get(sfxExpr.expr);
                ImcExpr mem = new ImcMEM(expr);
                ImcGen.exprImc.put(sfxExpr, mem);
                break;
        
            default:
                throw new InternalError("How did you get here? ImcGenerator, AstSfxExpr.");

        }
		return null;
	}

    @Override
	public Object visit(AstArrExpr arrExpr, Object arg) {
		arrExpr.arr.accept(this, arg);
		arrExpr.idx.accept(this, arg);
        ImcExpr arrayAddress = ((ImcMEM) ImcGen.exprImc.get(arrExpr.arr)).addr;
        ImcExpr index = ImcGen.exprImc.get(arrExpr.idx);

        SemType type = ((SemArrayType) SemAn.ofType.get(arrExpr.arr).actualType()).elemType;
        long typeSize = MemEvaluator.getTypeSize(type);

        ImcExpr size = new ImcCONST(typeSize);
        ImcExpr offset = new ImcBINOP(ImcBINOP.Oper.MUL, index, size);
        ImcExpr result = new ImcBINOP(ImcBINOP.Oper.ADD, arrayAddress, offset);

        result = new ImcMEM(result);

        ImcGen.exprImc.put(arrExpr, result);
		return null;
	}

    @Override
	public Object visit(AstCmpExpr cmpExpr, Object arg) {
		cmpExpr.expr.accept(this, arg);
        ImcExpr componentAddress = ((ImcMEM) ImcGen.exprImc.get(cmpExpr.expr)).addr;
        AstCmpDefn defn = (AstCmpDefn) SemAn.definedAt.get(cmpExpr);
        MemRelAccess access = (MemRelAccess) Memory.cmpAccesses.get(defn);

        ImcExpr offset = new ImcCONST(access.offset);
        ImcExpr result = new ImcBINOP(ImcBINOP.Oper.ADD, componentAddress, offset);

        result = new ImcMEM(result);

        ImcGen.exprImc.put(cmpExpr, result);
		return null;
	}

    @Override
	public Object visit(AstCallExpr callExpr, Object arg) {
        AstFunDefn defn = (AstFunDefn) SemAn.definedAt.get(callExpr);
        MemFrame frame = Memory.frames.get(defn);
        Vector<ImcExpr> expressions = new Vector<>();
        Vector<Long> offsets = new Vector<>();

        ImcExpr SL = null;
        MemFrame caller = framePointers.peek();

        if (defn.stmt == null || frame.depth == 0) {
            SL = new ImcCONST(42L);
        } else {
            SL = new ImcTEMP(caller.FP);
            long callerDepth = caller.depth;
            long myDepth = frame.depth;
            while (myDepth <= callerDepth) {
                SL = new ImcMEM(SL);
                myDepth += 1;
            }
        }
        expressions.add(SL);
        offsets.add(0L);

		if (callExpr.args != null) {
            int parameterIndex = 0;
            for (AstExpr node : callExpr.args) {
                node.accept(this, arg);
                ImcExpr argument = ImcGen.exprImc.get(node);
                AstParDefn param = defn.pars.get(parameterIndex);

                if (param instanceof AstRefParDefn) {
                    argument = ((ImcMEM) argument).addr;
                }
                expressions.add(argument);
                offsets.add(offsets.lastElement() + 8L);
                parameterIndex += 1;
            }
        }

        ImcExpr result = null;
        if (defn.stmt != null)
            result = new ImcCALL(frame.label, offsets, expressions);
        else
            result = new ImcCALL(new MemLabel(callExpr.name), offsets, expressions); // If its a prototype just return the label to call
		ImcGen.exprImc.put(callExpr, result);
		return null;
	}

    @Override
	public Object visit(AstCastExpr castExpr, Object arg) {
        castExpr.expr.accept(this, arg);
		castExpr.type.accept(this, arg);

        ImcExpr expression = ImcGen.exprImc.get(castExpr.expr);
        SemType type = SemAn.isType.get(castExpr.type);
        ImcExpr result = null;

        if (TypeResolver.equiv(type, SemCharType.type)) {
            ImcExpr constant = new ImcCONST(256L);
            ImcExpr modOp = new ImcBINOP(ImcBINOP.Oper.MOD, expression, constant);
            result = modOp;
            // ImcStmt stmt = new ImcMOVE(expression, modOp);
            // expression = ((ImcMEM) expression).addr;
            // result = new ImcSEXPR(stmt, expression);
            // result = new ImcMEM(result);

        } else {
            result = expression;
        }

        ImcGen.exprImc.put(castExpr, result);
		return null;
	}

    @Override
	public Object visit(AstSizeofExpr sizeofExpr, Object arg) {
		sizeofExpr.type.accept(this, arg);
        SemType type = SemAn.isType.get(sizeofExpr.type);
        Long size = MemEvaluator.getTypeSize(type);
        ImcExpr result = new ImcCONST(size);

        ImcGen.exprImc.put(sizeofExpr, result);
		return null;
	}

    // STATEMENTS

    @Override
	public Object visit(AstExprStmt exprStmt, Object arg) {
		exprStmt.expr.accept(this, arg);
        ImcExpr expr = ImcGen.exprImc.get(exprStmt.expr);
        ImcStmt stmt = new ImcESTMT(expr);
        ImcGen.stmtImc.put(exprStmt, stmt);
		return null;
	}

	@Override
	public Object visit(AstAssignStmt assignStmt, Object arg) {
		assignStmt.dst.accept(this, arg);
		assignStmt.src.accept(this, arg);
        ImcExpr expr1 = ((ImcMEM) ImcGen.exprImc.get(assignStmt.dst)).addr;
        ImcExpr expr2 = ImcGen.exprImc.get(assignStmt.src);

        ImcExpr memWrite = new ImcMEM(expr1);
        ImcStmt move = new ImcMOVE(memWrite, expr2);

        ImcGen.stmtImc.put(assignStmt, move);
		return null;
	}

    @Override
	public Object visit(AstBlockStmt blockStmt, Object arg) {
        Vector<ImcStmt> statements = new Vector<>();

		if (blockStmt.stmts != null) {
            for (AstStmt node : blockStmt.stmts) {
                node.accept(this, arg);
                ImcStmt stmt = ImcGen.stmtImc.get(node);
                statements.add(stmt);
            }
        }
        ImcStmt blockStatement = new ImcSTMTS(statements);
        ImcGen.stmtImc.put(blockStmt, blockStatement);
		return null;
	}

	@Override
	public Object visit(AstIfStmt ifStmt, Object arg) {
		ifStmt.cond.accept(this, arg);
        ImcExpr condition = ImcGen.exprImc.get(ifStmt.cond);
		ifStmt.thenStmt.accept(this, arg);
        ImcStmt stmt1 = ImcGen.stmtImc.get(ifStmt.thenStmt);
        ImcStmt stmt2 = null;
		if (ifStmt.elseStmt != null) {
			ifStmt.elseStmt.accept(this, arg);
            stmt2 = ImcGen.stmtImc.get(ifStmt.elseStmt);
        }
        MemLabel thenLabel = new MemLabel();
        MemLabel elseLabel = new MemLabel();
        MemLabel endLabel = new MemLabel();

        Vector<ImcStmt> stmts = new Vector<>();
        stmts.add(new ImcCJUMP(condition, thenLabel, elseLabel));
        stmts.add(new ImcLABEL(thenLabel));
        stmts.add(stmt1);
        if (stmt2 != null)
            stmts.add(new ImcJUMP(endLabel));
        stmts.add(new ImcLABEL(elseLabel));
        if (stmt2 != null)
            stmts.add(stmt2);
        stmts.add(new ImcLABEL(endLabel));
        
        ImcStmt result = new ImcSTMTS(stmts);
        ImcGen.stmtImc.put(ifStmt, result);
		return null;
	}

    @Override
	public Object visit(AstWhileStmt whileStmt, Object arg) {
		whileStmt.cond.accept(this, arg);
        ImcExpr condition = ImcGen.exprImc.get(whileStmt.cond);
		whileStmt.stmt.accept(this, arg);
        ImcStmt stmts = ImcGen.stmtImc.get(whileStmt.stmt);

        MemLabel whileLabel = new MemLabel();
        MemLabel startLabel = new MemLabel();
        MemLabel endLabel = new MemLabel();

        Vector<ImcStmt> statements = new Vector<>();
        statements.add(new ImcLABEL(whileLabel));
        statements.add(new ImcCJUMP(condition, startLabel, endLabel));
        statements.add(new ImcLABEL(startLabel));
        statements.add(stmts);
        statements.add(new ImcJUMP(whileLabel));
        statements.add(new ImcLABEL(endLabel));

        ImcStmt result = new ImcSTMTS(statements);
        ImcGen.stmtImc.put(whileStmt, result);
		return null;
	}

    @Override
	public Object visit(AstReturnStmt retStmt, Object arg) {
		retStmt.expr.accept(this, arg);
        ImcExpr expr = ImcGen.exprImc.get(retStmt.expr);

        MemTemp RV = framePointers.peek().RV;
        ImcExpr temp = new ImcTEMP(RV);
        ImcStmt move = new ImcMOVE(temp, expr);
        ImcStmt exit = new ImcJUMP(exitPoints.peek());

        Vector<ImcStmt> statements = new Vector<>();
        statements.add(move);
        statements.add(exit);

        ImcStmt result = new ImcSTMTS(statements);
        ImcGen.stmtImc.put(retStmt, result);
		return null;
	}

    
}
