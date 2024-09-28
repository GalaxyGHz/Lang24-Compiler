package lang24.phase.livean;

import lang24.data.mem.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import lang24.data.asm.*;
import lang24.data.imc.code.ImcInstr;
import lang24.phase.*;
import lang24.phase.asmgen.*;

public class LivenessAnalyzer {

    private HashMap<MemLabel, AsmOPER> labelsToASMLabels;
	private HashMap<AsmOPER, HashSet<AsmOPER>> succesors;

    public LivenessAnalyzer() {
		succesors = new HashMap<>();
		labelsToASMLabels = new HashMap<>();
	}

    public void analyze(Vector<AsmInstr> codeInstrs) {
		Vector<AsmOPER> instrs = new Vector<>();
		for (AsmInstr instr : codeInstrs) {
			AsmOPER newInstr = (AsmOPER) instr;
			instrs.add(newInstr);
			newInstr.removeAllFromIn();
			newInstr.removeAllFromOut();
		}
		findLabels(instrs);
		findSuccesors(instrs);
		int i = 0;
		// While something is changing continue working
		while (oneIterationOfLiveness(instrs)) {
			i += 1;
		};
		// System.out.println(i);
    }

    private boolean oneIterationOfLiveness(Vector<AsmOPER> instrs) {
		boolean continueAlgorithm = false;
		for (AsmOPER instr : instrs) {
			boolean changeOut = updateOut(instr);
			boolean changeIn = updateIn(instr);
	
			if (changeOut || changeIn) continueAlgorithm = true;
		}
		return continueAlgorithm;
	}

	private boolean updateOut(AsmOPER instr) { // out(n) = UNION in(succ) for succ in SUCCESOR(n)
		HashSet<MemTemp> startingOut = instr.out();
		instr.removeAllFromOut();
		for (AsmOPER succ : this.succesors.get(instr)) {
			if (succ != null)
				instr.addOutTemp(succ.in());
		}
		return !startingOut.equals(instr.out());
	}

	private boolean updateIn(AsmOPER instr) { // in(n) = use(n) UNION [out(n) DIFFERENCE def(n)]
		HashSet<MemTemp> startingIn = instr.in();
		instr.removeAllFromIn();
		Vector<MemTemp> uses = instr.uses();
		Vector<MemTemp> defs = instr.defs();
		HashSet<MemTemp> out = instr.out();
		out.removeAll(defs);
		out.addAll(uses);
		instr.addInTemps(out);
		return !startingIn.equals(instr.in());
	}

	private void findSuccesors(Vector<AsmOPER> instrs) {
		for (int index = 0; index < instrs.size(); index++) {
			AsmOPER instr = instrs.get(index);
			HashSet<AsmOPER> mySuccs = new HashSet<>();
			
			if (instr.jumps().size() != 0) { // If it has jumps, add them too
				for (MemLabel label : instr.jumps()) {
					mySuccs.add(this.labelsToASMLabels.get(label));
				}
			} else {
				if (index < instrs.size() - 1) 
					mySuccs.add(instrs.get(index + 1)); // Last instruction doesnt have a succesor, all others have one directly behind them
			}
			this.succesors.put(instr, mySuccs);
		}
	}

	private void findLabels(Vector<AsmOPER> instrs) {
		for (AsmOPER instr : instrs) {
			if (instr instanceof AsmLABEL) {
				this.labelsToASMLabels.put(((AsmLABEL) instr).getLabel(), instr);
			}
		}
	}
}
