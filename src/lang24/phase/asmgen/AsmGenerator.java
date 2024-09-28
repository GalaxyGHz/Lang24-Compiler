package lang24.phase.asmgen;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

import lang24.common.report.Report;
import lang24.data.asm.AsmInstr;
import lang24.data.asm.AsmLABEL;
import lang24.data.asm.AsmMOVE;
import lang24.data.asm.AsmOPER;
import lang24.data.imc.code.ImcInstr;
import lang24.data.imc.visitor.ImcVisitor;
import lang24.data.mem.MemLabel;
import lang24.data.mem.MemTemp;
import lang24.data.imc.code.expr.*;
import lang24.data.imc.code.stmt.*;

public class AsmGenerator implements ImcVisitor<MemTemp, Object> {

    private MemTemp FP;
    private Vector<AsmInstr> instructions;
    private Vector<MemTemp> createdRegister;
    
    public AsmGenerator(MemTemp FP) {
        this.FP = FP;
        this.instructions = new Vector<>();
        this.createdRegister = new Vector<>();
    }

    public Vector<AsmInstr> instructions() {
        return this.instructions;
    }

    private MemTemp chooseRegister(MemTemp srcReg1, MemTemp srcReg2) {
        if (srcReg1 != null && this.createdRegister.contains(srcReg1)) {
            return srcReg1;
        }
        if (srcReg2 != null && this.createdRegister.contains(srcReg2)) {
            return srcReg2;
        }
        MemTemp dstReg = new MemTemp();
        this.createdRegister.add(dstReg);
        return dstReg;
    }

    private void addInstruction(String instr, MemTemp dst, MemTemp src1, MemTemp src2, MemLabel label1, MemLabel label2, boolean isMove) {
        Vector<MemTemp> uses = null;
        Vector<MemTemp> defs = null;
        Vector<MemLabel> jumps = null;

        if (dst != null) defs = new Vector<>();
        if (src1 != null) uses = new Vector<>();
        if (label1 != null) jumps = new Vector<>();

        if (dst != null) defs.add(dst);
        if (src1 != null) uses.add(src1);
        if (src2 != null) uses.add(src2);
        if (label1 != null) jumps.add(label1);
        if (label2 != null) jumps.add(label2);

        if (uses != null) {
            Vector<MemTemp> newUses = new Vector<>();
            for (int i = 0; i < uses.size(); i++) {
                if (uses.get(i).equals(this.FP)) {
                    instr = instr.replace("`s" + i, "$253");
                    if (i == 0) {
                        instr = instr.replace("`s1", "`s0");
                    }
                }
                else 
                    newUses.add(uses.get(i));
            }
            uses = newUses.size() == 0 ? null : newUses;
        }
		// if (defs != null) { DOESNT HAPPEN, WE NEVER STORE TO FP
        //     Vector<MemTemp> newDefs = new Vector<>();
        //     for (int i = 0; i < defs.size(); i++) {
        //         if (defs.get(i).equals(this.FP)) 
        //             instr = instr.replace("`d" + i, "$253");
        //         else
        //             newDefs.add(defs.get(i));
        //     }
        //     defs = newDefs.size() == 0 ? null : newDefs;
        // }

        AsmOPER asmOper;
        if (isMove == true) asmOper = new AsmMOVE(instr, uses, defs);
        else asmOper = new AsmOPER(instr, uses, defs, jumps);

        this.instructions.add(asmOper);
    }



    public MemTemp visit(ImcCALL call, Object arg) {
        long i = 0;
        for (ImcExpr expr : call.args) {
            MemTemp valReg = expr.accept(this, arg);
            addInstruction("STO `s0,$254," + String.valueOf(i*8), null, valReg, null, null, null, false);
            i += 1;
        }
        addInstruction("PUSHJ $8," + call.label.name, null, null, null, null, null, false);
        return null;
    }

    public MemTemp visit(ImcMOVE move, Object arg) {
        if (move.dst instanceof ImcTEMP && (move.src instanceof ImcBINOP || move.src instanceof ImcUNOP || move.src instanceof ImcMEM || 
                                            move.src instanceof ImcCONST || move.src instanceof ImcNAME || move.src instanceof ImcTEMP)) { // Not sure if ImcNAME is needed, doesnt appear anywhere in tests
            MemTemp dstReg = move.dst.accept(this, arg);
            MemTemp srcReg = move.src.accept(this, arg);
            addInstruction("ADD `d0,`s0,0", dstReg, srcReg, null, null, null, true);

        } else if (move.dst instanceof ImcTEMP && move.src instanceof ImcCALL) {
            MemTemp dstReg = move.dst.accept(this, arg);
            move.src.accept(this, arg);
            addInstruction("LDO `d0,$254,0", dstReg, null, null, null, null, false);

        } 
        // else if (move.dst instanceof ImcTEMP && move.src instanceof ImcTEMP) {
        //     MemTemp dstReg = move.dst.accept(this, arg);
        //     MemTemp srcReg = move.src.accept(this, arg);
        //     addInstruction("ADD `d0, `s0, 0", dstReg, srcReg, null, null, null, true);

        // }
        else if (move.dst instanceof ImcMEM) {
            MemTemp memReg = ((ImcMEM) move.dst).addr.accept(this, arg);
            MemTemp valReg = move.src.accept(this, arg);
            addInstruction("STO `s0,`s1,0", null, valReg, memReg, null, null, false);

        } else {
            System.out.println("How did you get hereeeee, it means that there is a Move that doesnt move into a reg or mem???");
            throw new Report.InternalError();
        }
        return null;
    }

    public MemTemp visit(ImcUNOP unOp, Object arg) {
        MemTemp srcReg = unOp.subExpr.accept(this, arg);
        // MemTemp dstReg = arg == null ? chooseRegister(srcReg, null) : (MemTemp) arg;
        MemTemp dstReg = new MemTemp();

        switch (unOp.oper) {
            case ImcUNOP.Oper.NEG:
                addInstruction("NEG `d0,`s0", dstReg, srcReg, null, null, null, false);
                return dstReg;
            case ImcUNOP.Oper.NOT:
                addInstruction("ZSZ `d0,`s0,1", dstReg, srcReg, null, null, null, false);
                return dstReg;

            default:
                throw new InternalError("How did you get here? AsmGenerator.");
        }
    }

    public MemTemp visit(ImcBINOP binOp, Object arg) {
        MemTemp srcReg1 = binOp.fstExpr.accept(this, arg);

        switch (binOp.oper) { // TODO: CE NEKI NE DELA PROBAJ TO ZAKOMENTIRATI
            case ImcBINOP.Oper.ADD: // Useful optimization for storing and loading with offsets
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value >= -255 && ((ImcCONST) binOp.sndExpr).value < 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("SUB `d0,`s0," + Math.abs(((ImcCONST) binOp.sndExpr).value), dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value <= 255 && ((ImcCONST) binOp.sndExpr).value > 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0," + ((ImcCONST) binOp.sndExpr).value, dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value == 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0,0", dstRegOpt, srcReg1, null, null, null, true);
                    return dstRegOpt;
                }
                break;
            case ImcBINOP.Oper.SUB: // Useful optimization for storing and loading with offsets
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value >= -255 && ((ImcCONST) binOp.sndExpr).value < 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0," + Math.abs(((ImcCONST) binOp.sndExpr).value), dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value <= 255 && ((ImcCONST) binOp.sndExpr).value > 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("SUB `d0,`s0," + ((ImcCONST) binOp.sndExpr).value, dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value == 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0,0", dstRegOpt, srcReg1, null, null, null, true);
                    return dstRegOpt;
                }
                break;
            case ImcBINOP.Oper.MUL:
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value == 1) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0,0", dstRegOpt, srcReg1, null, null, null, true);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value <= 255 && ((ImcCONST) binOp.sndExpr).value > 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("MUL `d0,`s0," + ((ImcCONST) binOp.sndExpr).value, dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                break;
            case ImcBINOP.Oper.DIV:
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value == 1) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("ADD `d0,`s0,0", dstRegOpt, srcReg1, null, null, null, true);
                    return dstRegOpt;
                }
                if (binOp.sndExpr instanceof ImcCONST && ((ImcCONST) binOp.sndExpr).value <= 255 && ((ImcCONST) binOp.sndExpr).value > 0) {
                    // MemTemp dstRegOpt = arg == null ? chooseRegister(srcReg1, null) : (MemTemp) arg;
                    MemTemp dstRegOpt = new MemTemp();
                    addInstruction("DIV `d0,`s0," + ((ImcCONST) binOp.sndExpr).value, dstRegOpt, srcReg1, null, null, null, false);
                    return dstRegOpt;
                }
                break;
            default:
                break;
        }

        MemTemp srcReg2 = binOp.sndExpr.accept(this, null); // It has too be null because if we get a reg in arg and send it down both ways, the right one will overwrite the left ones work

        String operator;
        // MemTemp dstReg = arg == null ? chooseRegister(srcReg1, srcReg2) : (MemTemp) arg;
        MemTemp dstReg = new MemTemp();

        switch (binOp.oper) {
            case ImcBINOP.Oper.OR:
                operator = "OR";
                break;
            case ImcBINOP.Oper.AND:
                operator = "AND";
                break;
            case ImcBINOP.Oper.EQU:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSZ `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.NEQ:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSNZ `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.LTH:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSN `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.GTH:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSP `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.LEQ:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSNP `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.GEQ:
                addInstruction("CMP `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("ZSNN `d0,`s0,1", dstReg, dstReg, null, null, null, false);
                return dstReg;
            case ImcBINOP.Oper.ADD:
                operator = "ADD";
                break;
            case ImcBINOP.Oper.SUB:
                operator = "SUB";
                break;
            case ImcBINOP.Oper.MUL:
                operator = "MUL";
                break;
            case ImcBINOP.Oper.DIV:
                operator = "DIV";
                break;
            case ImcBINOP.Oper.MOD:
                addInstruction("DIV `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
                addInstruction("GET `d0,rR", dstReg, null, null, null, null, false);
                return dstReg;
        
            default:
                throw new InternalError("How did you get here? AsmGenerator.");
        }
        addInstruction(operator + " `d0,`s0,`s1", dstReg, srcReg1, srcReg2, null, null, false);
        return dstReg;
    }

    public MemTemp visit(ImcCJUMP cjump, Object arg) {
        MemTemp srcReg = cjump.cond.accept(this, arg);
        addInstruction("BNZ `s0," + cjump.posLabel.name, null, srcReg, null, cjump.posLabel, cjump.negLabel, false);
        return null;
    }

    public MemTemp visit(ImcJUMP jump, Object arg) {
        addInstruction("JMP " + jump.label.name, null, null, null, jump.label, null, false);
        return null;
    }

    public MemTemp visit(ImcESTMT eStmt, Object arg) {
        eStmt.expr.accept(this, arg);
        return null;
    }

    public MemTemp visit(ImcLABEL label, Object arg) {
        AsmLABEL asmLabel = new AsmLABEL(label.label);
        this.instructions.add(asmLabel);
        return null;
    }

    public MemTemp visit(ImcMEM mem, Object arg) {
        if (mem.addr instanceof ImcBINOP && ((ImcBINOP) mem.addr).sndExpr instanceof ImcCONST &&
            ((ImcCONST) ((ImcBINOP) mem.addr).sndExpr).value >= -255 && ((ImcCONST) ((ImcBINOP) mem.addr).sndExpr).value <= 255) {
            MemTemp base = ((ImcBINOP) mem.addr).fstExpr.accept(this, arg);
            ImcCONST constant = (ImcCONST) ((ImcBINOP) mem.addr).sndExpr;
            long offset = constant.value;

            // MemTemp dstReg = arg == null ? chooseRegister(base, null) : (MemTemp) arg;
            MemTemp dstReg = new MemTemp();
            if (offset < 0) {
                addInstruction("SUB `d0,`s0," + String.valueOf(Math.abs(offset)), dstReg, base, null, null, null, false);
                addInstruction("LDO `d0,`s0,0", dstReg, dstReg, null, null, null, false);

            } else {
                addInstruction("LDO `d0,`s0," + String.valueOf(offset), dstReg, base, null, null, null, false);
            }
            return dstReg;
        } else {
            MemTemp srcReg = mem.addr.accept(this, arg);
            // MemTemp dstReg = arg == null ? chooseRegister(srcReg, null) : (MemTemp) arg;
            MemTemp dstReg = new MemTemp();
            addInstruction("LDO `d0,`s0,0", dstReg, srcReg, null, null, null, false);
            return dstReg;
        }
    }

    public MemTemp visit(ImcTEMP temp, Object arg) {
        return temp.temp;
    }


    public MemTemp visit(ImcNAME name, Object arg) {
         // TODO: not sure if this is legal her, i.e. can we immediately reuse this reg after we fetch the address? Probably
        // MemTemp dstReg = arg == null ? chooseRegister(null, null) : (MemTemp) arg;
        MemTemp dstReg = new MemTemp();
        addInstruction("LDA `d0," + name.label.name, dstReg, null, null, null, null, false);
        return dstReg;
    }

    public MemTemp visit(ImcCONST constant, Object arg) {
        long mask = 0xFFFFl;
        // MemTemp dstReg = arg == null ? chooseRegister(null, null) : (MemTemp) arg;
        MemTemp dstReg = new MemTemp();
        long value = constant.value >= -255l && constant.value < 0 ? Math.abs(constant.value) : constant.value;

        if (value == 0) {
            addInstruction("SETL `d0,0", dstReg, null, null, null, null, false);
        } else {
            String setInstrs[] = {"SETL", "SETML", "SETMH", "SETH"};
            String incInstrs[] = {"INCL", "INCML", "INCMH", "INCH"};
            int start = 0;
            while ((value & mask) == 0) {
                start += 1;
                value = value >> 16;
            }
            long asmValue = value & mask;
            addInstruction(setInstrs[start] + " `d0," + String.valueOf(asmValue), dstReg, null, null, null, null, false);
            start += 1;
            for (int index = start; index < 4; index++) {
                value = value >> 16;
                asmValue = value & mask;
                if (asmValue != 0) {
                    addInstruction(incInstrs[index] + " `d0," + String.valueOf(asmValue), dstReg, null, null, null, null, false);
                }
            }
            // If it is negative, but we can load the positive value in one SET, we load it then negate it.
            // This is two istructions, loading -1 for instance is 4 instructions
            if (constant.value >= -255l && constant.value < 0) {
                addInstruction("NEG `d0,`s0", dstReg, dstReg, null, null, null, false);
            }
        }
        return dstReg;
    }
}