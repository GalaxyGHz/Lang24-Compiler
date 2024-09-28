package lang24.phase.seman;

import lang24.data.ast.tree.expr.*;
import lang24.data.ast.visitor.*;

/**
 * Lvalue resolver.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class LValResolver implements AstFullVisitor<Object, Object> {

	/** Constructs a new lvalue resolver. */
	public LValResolver() {
	}

	@Override
	public Object visit(AstArrExpr arrExpr, Object arg) {
		arrExpr.arr.accept(this, arg);
		arrExpr.idx.accept(this, arg);
		if (SemAn.isLVal.get(arrExpr.arr))
			SemAn.isLVal.put(arrExpr, true);
		else
			SemAn.isLVal.put(arrExpr, false);
		return null;
	}

	@Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
		SemAn.isLVal.put(atomExpr, false);
		return null;
	}

	@Override
	public Object visit(AstBinExpr binExpr, Object arg) {
		SemAn.isLVal.put(binExpr, false);
		binExpr.fstExpr.accept(this, arg);
		binExpr.sndExpr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AstCallExpr callExpr, Object arg) {
		SemAn.isLVal.put(callExpr, false);
		if (callExpr.args != null)
			callExpr.args.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AstCastExpr castExpr, Object arg) {
		castExpr.type.accept(this, arg);
		castExpr.expr.accept(this, arg);
		if (SemAn.isLVal.get(castExpr.expr)) {
			// SemAn.isLVal.put(castExpr, true);
			SemAn.isLVal.put(castExpr, false);
		}
		else
			SemAn.isLVal.put(castExpr, false);
		return null;
	}

	@Override
	public Object visit(AstCmpExpr cmpExpr, Object arg) {
		cmpExpr.expr.accept(this, arg);
		if (SemAn.isLVal.get(cmpExpr.expr))
			SemAn.isLVal.put(cmpExpr, true);
		else
			SemAn.isLVal.put(cmpExpr, false);
		return null;
	}

	@Override
	public Object visit(AstNameExpr nameExpr, Object arg) {
		SemAn.isLVal.put(nameExpr, true);
		return null;
	}

	@Override
	public Object visit(AstPfxExpr pfxExpr, Object arg) {
		SemAn.isLVal.put(pfxExpr, false);
		pfxExpr.expr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AstSfxExpr sfxExpr, Object arg) {
		SemAn.isLVal.put(sfxExpr, true);
		sfxExpr.expr.accept(this, arg);
		return null;
	}

	@Override
	public Object visit(AstSizeofExpr sizeofExpr, Object arg) {
		SemAn.isLVal.put(sizeofExpr, false);
		sizeofExpr.type.accept(this, arg);
		return null;
	}


}
