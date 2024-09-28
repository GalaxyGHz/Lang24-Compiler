package lang24.phase.regall;

import java.util.*;

import lang24.data.mem.*;
import lang24.data.asm.*;
import lang24.phase.*;
import lang24.phase.asmgen.*;
import lang24.phase.livean.LiveAn;
import lang24.phase.livean.LivenessAnalyzer;

/**
 * Register allocation.
 */
public class RegAll extends Phase {

	/** Mapping of temporary variables to registers. */
	public static final HashMap<MemTemp, Integer> tempToReg = new HashMap<MemTemp, Integer>();
	private int numRegs;

	public RegAll(int numRegs) {
		super("regall");
		this.numRegs = numRegs;
	}

	public void allocate() {
		changePUSHJ();

		RegisterAllocator allocator = new RegisterAllocator(this.numRegs);

		allocator.main();
	
		for (MemTemp tmp : allocator.getColors().keySet()) {
			tempToReg.put(tmp, allocator.getColors().get(tmp));
		}
		removeRedundantMoves();
	}

	private void changePUSHJ() {
		for (Code code : AsmGen.codes) {
			for (AsmInstr instr : code.instrs) {
				AsmOPER operInstr = (AsmOPER) instr;
				operInstr.setInstr(operInstr.instr().replace("PUSHJ $8", "PUSHJ $" + numRegs));
			}
		}
	}

	private void removeRedundantMoves() {
		for (Code code : AsmGen.codes) {
			for (int i = 0; i < code.instrs.size(); i++) {
				AsmOPER asm = (AsmOPER) code.instrs.get(i);
				if (asm instanceof AsmMOVE && tempToReg.get(asm.uses().toArray()[0]) == tempToReg.get(asm.defs().toArray()[0])) {
					code.instrs.remove(i);
					i -= 1;
				}
			}
		}
	}

	public void log() {
		if (logger == null)
			return;
		for (Code code : AsmGen.codes) {
			logger.begElement("code");
			logger.addAttribute("body", code.entryLabel.name);
			logger.addAttribute("epilogue", code.exitLabel.name);
			logger.addAttribute("tempsize", Long.toString(code.tempSize));
			code.frame.log(logger);
			logger.begElement("instructions");
			for (AsmInstr instr : code.instrs) {
				logger.begElement("instruction");
				logger.addAttribute("code", instr.toString(tempToReg));
				logger.begElement("temps");
				logger.addAttribute("name", "use");
				for (MemTemp temp : instr.uses()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "def");
				for (MemTemp temp : instr.defs()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "in");
				for (MemTemp temp : instr.in()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.begElement("temps");
				logger.addAttribute("name", "out");
				for (MemTemp temp : instr.out()) {
					logger.begElement("temp");
					logger.addAttribute("name", temp.toString());
					logger.endElement();
				}
				logger.endElement();
				logger.endElement();
			}
			logger.endElement();
			logger.endElement();
		}
	}

}
