package lang24.phase.regall;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.Vector;

import lang24.data.asm.AsmInstr;
import lang24.data.asm.AsmMOVE;
import lang24.data.asm.AsmOPER;
import lang24.data.asm.Code;
import lang24.data.mem.MemTemp;
import lang24.phase.asmgen.AsmGen;
import lang24.phase.livean.LiveAn;

public class RegisterAllocator {

	private int numRegs;
	private HashSet<MemTemp> nodesToAvoid;

	private HashSet<MemTemp> initial;
	private HashSet<MemTemp> simplifyWorklist;
	private HashSet<MemTemp> freezeWorklist;
	private HashSet<MemTemp> spillWorklist;
	private HashSet<MemTemp> spilledNodes;
	private HashSet<MemTemp> coalescedNodes;
	private HashSet<MemTemp> coloredNodes;
	private Stack<MemTemp> selectStack;

	private HashSet<AsmOPER> coalescedMoves;
	private HashSet<AsmOPER> constrainedMoves;
	private HashSet<AsmOPER> frozenMoves;
	private HashSet<AsmOPER> worklistMoves;
	private HashSet<AsmOPER> activeMoves;

	private HashMap<MemTemp, HashSet<MemTemp>> adjList;
	private HashMap<MemTemp, Integer> degree;
	private HashMap<MemTemp, HashSet<AsmOPER>> moveList;
	private HashMap<MemTemp, MemTemp> alias;
	private HashMap<MemTemp, Integer> colors;

	private HashMap<MemTemp, Integer> heuristic;

    public RegisterAllocator(int numRegs) {
		nodesToAvoid = new HashSet<>();
		this.numRegs = numRegs;
	}

	public HashMap<MemTemp, Integer> getColors() {
		return colors;
	}

	int i = 0;

    public void main() {
		init();
		build();

		// for (MemTemp tmp : initial) {
		// 	System.out.println(degree.get(tmp));
		// }
		// return;

		makeWorklist();
		while (!(simplifyWorklist.size() == 0 && worklistMoves.size() == 0 && freezeWorklist.size() == 0 && spillWorklist.size() == 0)) {
			if (simplifyWorklist.size() != 0) {
				simplify();
			} else if (worklistMoves.size() != 0) {
				coalesce();
			} else if (freezeWorklist.size() != 0) {
				freeze();
			} else if (spillWorklist.size() != 0) {
				selectSpill();
			}
		}
		assignColors();

		if (spilledNodes.size() != 0) {
			rewriteProgram();
			try (LiveAn liveness = new LiveAn()) {
				liveness.analysis();
				liveness.log();
			}
			// if (this.i == 2) return;
			this.i += 1;
			main();
		}
    }

	private void assignColors() {
		while (selectStack.size() != 0) {
			MemTemp n = selectStack.pop();
			HashSet<Integer> okColors = new HashSet<>();
			for (int index = 0; index < numRegs; index++) {
				okColors.add(index);
			}
			for (MemTemp w : adjList.get(n)) {
				if (coloredNodes.contains(getAlias(w))) {
					okColors.remove(colors.get(getAlias(w)));
				}
			}
			if (okColors.size() == 0) spilledNodes.add(n);
			else {
				coloredNodes.add(n);
				int myColor = (Integer) okColors.toArray()[0];
				colors.put(n, myColor);
			}
		}
		for (MemTemp n : coalescedNodes) {
			colors.put(n, colors.get(getAlias(n)));
		}
	}

	private void init() {
		initial = new HashSet<>();
		simplifyWorklist = new HashSet<>();
		freezeWorklist = new HashSet<>();
		spillWorklist = new HashSet<>();
		spilledNodes = new HashSet<>();
		coalescedNodes = new HashSet<>();
		coloredNodes = new HashSet<>();
		selectStack = new Stack<>();
	
		coalescedMoves = new HashSet<>();
		constrainedMoves = new HashSet<>();
		frozenMoves = new HashSet<>();
		worklistMoves = new HashSet<>();
		activeMoves = new HashSet<>();
		
		adjList = new HashMap<>();
		degree = new HashMap<>();
		moveList = new HashMap<>();
		alias = new HashMap<>();
		colors = new HashMap<>();

		heuristic = new HashMap<>();

		for (Code code : AsmGen.codes) {
			for (AsmInstr instr : code.instrs) {
				AsmOPER asm = (AsmOPER) instr;
				initial.addAll(asm.in());
				initial.addAll(asm.out());
				initial.addAll(asm.uses());
				initial.addAll(asm.defs());
			}
		}
		for (MemTemp temp : initial) {
			adjList.put(temp, new HashSet<>());
			degree.put(temp, 0);
			moveList.put(temp, new HashSet<>());
			heuristic.put(temp, 0);
		}

		for (Code code : AsmGen.codes) {
			for (AsmInstr instr : code.instrs) {
				AsmOPER asm = (AsmOPER) instr;
				for (MemTemp i : asm.in()) {
					heuristic.put(i, heuristic.get(i) + 1);
				}
				for (MemTemp o : asm.out()) {
					heuristic.put(o, heuristic.get(o) + 1);
				}
			}
		}

	}

	// In the book
	private void build() {
		for (Code code : AsmGen.codes) {
			for (int index = code.instrs.size() - 1; index >= 0 ; index--) {
				AsmOPER asm = (AsmOPER) code.instrs.get(index);

				// Add edges to adjecency graph
				for (MemTemp src : asm.in()) {
					for (MemTemp dst : asm.in()) {
						addEdge(src, dst);
					}
				}
				for (MemTemp src : asm.out()) {
					for (MemTemp dst : asm.out()) {
						addEdge(src, dst);
					}
				}
				// Tick off move instructions as per the instructions in the book
				Vector<MemTemp> allMoveRelated = asm.uses();
				allMoveRelated.addAll(asm.defs());
				if (asm instanceof AsmMOVE) {
					for (MemTemp src : allMoveRelated) {
						moveList.get(src).add(asm);
					}
					worklistMoves.add(asm);
				}
			}
		}
	}

	private void addEdge(MemTemp src, MemTemp dst) {
		if (src != dst && !this.adjList.get(src).contains(dst)) {
			adjList.get(src).add(dst);
			degree.put(src, degree.get(src) + 1);
			adjList.get(dst).add(src);
			degree.put(dst, degree.get(dst) + 1);
		}
	}

	private void makeWorklist() {
		for (MemTemp temp : initial) {
			if (degree.get(temp) >= numRegs) {
				spillWorklist.add(temp);
			} else if (moveRelated(temp)) {
				freezeWorklist.add(temp);
			} else {
				simplifyWorklist.add(temp);
			}
		}
		initial.clear();
	}

	private boolean moveRelated(MemTemp temp) {
		return nodeMoves(temp).size() != 0;
	}

	private HashSet<AsmOPER> nodeMoves(MemTemp temp) {
		HashSet<AsmOPER> result = new HashSet<>();
		result.addAll(activeMoves);
		result.addAll(worklistMoves);
		result.retainAll(moveList.get(temp));
		return result;
	}

	private HashSet<MemTemp> adjacent(MemTemp temp) {
		HashSet<MemTemp> result = new HashSet<>();
		HashSet<MemTemp> removeMe = new HashSet<>();
		result.addAll(adjList.get(temp));
		removeMe.addAll(selectStack);
		removeMe.addAll(coalescedNodes);
		result.removeAll(removeMe);
		return result;
	}

	private void simplify() {
		MemTemp temp = (MemTemp) simplifyWorklist.toArray()[0];
		simplifyWorklist.remove(temp);
		selectStack.push(temp);
		for (MemTemp neighbor : adjacent(temp)) {
			decrementDegree(neighbor);
		}
	}

	private void decrementDegree(MemTemp m) {
		int d = degree.get(m);
		degree.put(m, d - 1);
		if (d == numRegs) {
			HashSet<MemTemp> change = new HashSet<>();
			change.add(m);
			change.addAll(adjacent(m));
			enableMoves(change);
			spillWorklist.remove(m);
			if (moveRelated(m)) {
				freezeWorklist.add(m);
			} else {
				simplifyWorklist.add(m);
			}
		}
	}

	private void enableMoves(HashSet<MemTemp> temps) {
		for (MemTemp n : temps) {
			for (AsmOPER m : nodeMoves(n)) {
				if (activeMoves.contains(m)) {
					activeMoves.remove(m);
					worklistMoves.add(m);
				}	
			}
		}
	}
    
	private void addWorkList(MemTemp u) {
		if (!moveRelated(u) && degree.get(u) < numRegs) {
			freezeWorklist.remove(u);
			simplifyWorklist.add(u);
		}
	}

	private boolean conservative(HashSet<MemTemp> temps) {
		int k = 0;
		for (MemTemp n : temps) {
			if (degree.get(n) >= numRegs) k += 1;
		}
		return k < numRegs;
	}

	private void coalesce() {
		AsmOPER m = (AsmOPER) worklistMoves.toArray()[0];
		MemTemp x = m.defs().get(0); // TODO: not sure
		MemTemp y = m.uses().get(0);

		x = getAlias(x);
		y = getAlias(y);

		worklistMoves.remove(m);
		MemTemp u = x;
		MemTemp v = y;

		HashSet<MemTemp> checkConservative = new HashSet<>();
		checkConservative.addAll(adjacent(u));
		checkConservative.addAll(adjacent(v));

		if (u == v) {
			coalescedMoves.add(m);
			addWorkList(u);
		} else if (adjList.get(u).contains(v)) {
			constrainedMoves.add(m);
			addWorkList(u);
			addWorkList(v);
		} else if (conservative(checkConservative)) {
			coalescedMoves.add(m);
			combine(u, v);
			addWorkList(u);
		} else {
			activeMoves.add(m);
		}
	}

	private MemTemp getAlias(MemTemp n) {
		if (coalescedNodes.contains(n)) return getAlias(alias.get(n));
		else return n;
	}

	private void combine(MemTemp u, MemTemp v) {
		if (freezeWorklist.contains(v)) {
			freezeWorklist.remove(v);
		} else {
			spillWorklist.remove(v);
		}
		coalescedNodes.add(v);
		alias.put(v, u);
		moveList.get(u).addAll(moveList.get(v));

		HashSet<MemTemp> enableMe = new HashSet<>();
		enableMe.add(v);
		enableMoves(enableMe);
		
		for (MemTemp t : adjacent(v)) {
			addEdge(t, u);
			decrementDegree(t);
		}

		if (degree.get(u) >= numRegs && freezeWorklist.contains(u)) {
			freezeWorklist.remove(u);
			spillWorklist.add(u);
		}
	}

	private void freeze() {
		MemTemp u = (MemTemp) freezeWorklist.toArray()[0];
		freezeWorklist.remove(u);
		simplifyWorklist.add(u);
		freezeMoves(u);
	}

	private void freezeMoves(MemTemp u) {
		for (AsmOPER m : nodeMoves(u)) {
			MemTemp x = m.defs().get(0); // TODO: not sure
			MemTemp y = m.uses().get(0);
			MemTemp v;
			if (getAlias(y) == getAlias(u)) {
				v = getAlias(x);
			} else {
				v = getAlias(y);
			}
			activeMoves.remove(m);
			frozenMoves.add(m);
			if (freezeWorklist.contains(v) && nodeMoves(v).size() == 0) {
				freezeWorklist.remove(v);
				simplifyWorklist.add(v);
			}
		}
	}

	private void selectSpill() {
		PriorityQueue<MemTemp> pq = new PriorityQueue<>((a, b) -> heuristic.get(b) - heuristic.get(a));
		for (MemTemp tmp : spillWorklist) {
			pq.add(tmp);
		}
		MemTemp m = pq.poll();
		while (nodesToAvoid.contains(m)) {
			m = pq.poll();
		}
		// System.out.println(m);
		spillWorklist.remove(m);
		simplifyWorklist.add(m);
		freezeMoves(m);
	}

	private Vector<AsmInstr> loadStoreInstructions(MemTemp temp, long offset, boolean isDef) {
		Vector<AsmInstr> loadStoreInstrs = new Vector<>();
		Vector<MemTemp> offsetVec = new Vector<>();
		Vector<MemTemp> dstVec = new Vector<>();
		MemTemp offsetReg = new MemTemp();
		offsetVec.add(offsetReg);
		dstVec.add(temp);
		long mask = 0xFFFFl;
		nodesToAvoid.add(temp);
		nodesToAvoid.add(offsetReg);

		String setInstrs[] = {"SETL", "SETML", "SETMH", "SETH"};
		String incInstrs[] = {"INCL", "INCML", "INCMH", "INCH"};
		int start = 0;
		while ((offset & mask) == 0) {
			start += 1;
			offset = offset >> 16;
		}
		long asmValue = offset & mask;
		loadStoreInstrs.add(new AsmOPER(setInstrs[start] + " `d0," + String.valueOf(asmValue), null, offsetVec, null));
		start += 1;
		for (int index = start; index < 4; index++) {
			offset = offset >> 16;
			asmValue = offset & mask;
			if (asmValue != 0) {
				loadStoreInstrs.add(new AsmOPER(incInstrs[index] + " `d0," + String.valueOf(asmValue), null, offsetVec, null));
			}
		}

		loadStoreInstrs.add(new AsmOPER("SUB `d0,$253,`s0", offsetVec, offsetVec, null));

		if (isDef) {
			dstVec.addAll(offsetVec);
			loadStoreInstrs.add(new AsmOPER("STO `s0,`s1,0", dstVec, null, null));
		} else {
			loadStoreInstrs.add(new AsmOPER("LDO `d0,`s0,0", offsetVec, dstVec, null));
		}

		return loadStoreInstrs;
	}

	private void rewriteProgram() {
		// for (MemTemp oper : spilledNodes) {
			// System.out.println(oper);
		// }

		HashMap<MemTemp, Long> offsets = new HashMap<>();

		// Do this for each block
		for (Code code : AsmGen.codes) {
			Vector<AsmInstr> newInstrs = new Vector<>();
			// Go over all instructions
			for (AsmInstr instr : code.instrs) {
				AsmOPER asm = (AsmOPER) instr;
				Vector<AsmInstr> loads = new Vector<>();
				Vector<AsmInstr> stores = new Vector<>();

				for (MemTemp use : asm.uses()) {
					if (spilledNodes.contains(use)) {
						loads.addAll(loadStoreInstructions(use, offsets.get(use), false));
					}
				}

				for (MemTemp def : asm.defs()) {
					if (spilledNodes.contains(def)) {
						code.tempSize += 8;
						offsets.put(def, code.frame.locsSize + 16 + code.tempSize);
						stores.addAll(loadStoreInstructions(def, offsets.get(def), true));
					}
				}
				newInstrs.addAll(loads);
				newInstrs.add(asm);
				newInstrs.addAll(stores);
			}
			code.instrs.clear();
        	code.instrs.addAll(newInstrs);
		}
	}


}
