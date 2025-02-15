package lang24.data.asm;

import java.util.*;
import lang24.data.mem.*;

/**
 * An assembly label.
 */
public class AsmLABEL extends AsmOPER {

	/** The label. */
	private final MemLabel label;

	public AsmLABEL(MemLabel label) {
		super("", null, null, null);
		this.label = label;
	}

	public MemLabel getLabel() {
		return this.label;
	}

	@Override
	public String toString() {
		return label.name;
	}

	@Override
	public String toString(HashMap<MemTemp, Integer> regs) {
		return label.name;
	}

}
