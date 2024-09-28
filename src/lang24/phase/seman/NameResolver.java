package lang24.phase.seman;

import java.util.ArrayList;

import lang24.common.report.*;
import lang24.data.ast.tree.*;
import lang24.data.ast.tree.defn.*;
import lang24.data.ast.tree.expr.*;
import lang24.data.ast.tree.type.*;
import lang24.data.ast.visitor.*;

/**
 * Name resolver.
 * 
 * The name resolver connects each node of a abstract syntax tree where a name
 * is used with the node where it is defined. The only exceptions are struct and
 * union component names which are connected with their definitions by type
 * resolver. The results of the name resolver are stored in
 * {@link lang24.phase.seman.SemAn#definedAt}.
 */
public class NameResolver implements AstFullVisitor<Object, Object> {

	/** Constructs a new name resolver. */
	public NameResolver() {
	}

	/** The symbol table. */
	private SymbTable symbTable = new SymbTable();

	private enum Pass {
		FIRST_TYPE,
		SECOND_TYPE,
		FIRST_FUNC,
		SECOND_FUNC,
	  }

	@Override
	public Object visit(AstNodes<? extends AstNode> nodes, Object __) {

		for (int i = 0; i < 5; i++) {
			for (final AstNode node : nodes) {
				switch (i) {
					case 0:
						if (node instanceof AstTypDefn) node.accept(this, Pass.FIRST_TYPE);
						break;
					case 1:
						if (node instanceof AstTypDefn) node.accept(this, Pass.SECOND_TYPE);
						break;
					case 2:
						if (!(node instanceof AstTypDefn || node instanceof AstFunDefn))
							node.accept(this, __);
						break;
					case 3:
						if (node instanceof AstFunDefn) node.accept(this, Pass.FIRST_FUNC);
						break;
					case 4:
						if (node instanceof AstFunDefn) node.accept(this, Pass.SECOND_FUNC);
						break;
					default:
						throw new Report.InternalError();
				}
			}
		}
		return null;
	}

	private void insertHandled(AstDefn node, String err) {
		try {
			this.symbTable.ins(node.name, node);
		} catch (Exception e) {
			throw new Report.Error(node.location(), err);
		}
	}

	private AstDefn findHandled(String name, Location loc, String err) {
		AstDefn def = null;
		try {
			def = symbTable.fnd(name);
		} catch (Exception e) {
			throw new Report.Error(loc, err);
		}
		return def;
	}

	@Override
	public Object visit(AstVarDefn var, Object __) {
		this.insertHandled(var, "Semantic error: Variable name '" + var.name + "' already defined!");
		var.type.accept(this, __);
		return null;
	}

	@Override
	public Object visit(AstNameExpr name, Object __) {
		AstDefn def = findHandled(name.name, name.location(), "Semantic error: Variable name '" + name.name + "' not found!");
		SemAn.definedAt.put(name, def);
		return null;
	}

	@Override
	public Object visit(AstTypDefn type, Object pass) {
		if (pass != Pass.SECOND_TYPE) {
			this.insertHandled(type, "Semantic error: Type name '" + type.name + "' already defined!");
		}
		type.type.accept(this, pass);
		return null;
	}

	@Override
	public Object visit(AstNameType name, Object pass) {
		if (pass == Pass.FIRST_TYPE) return null;
		else {
			AstDefn def = findHandled(name.name, name.location(), "Semantic error: Type name '" + name.name + "' not found!");
			SemAn.definedAt.put(name, def);
		}
		return null;
	}

	@Override
	public Object visit(AstFunDefn func, Object pass) {
		if (pass == Pass.FIRST_FUNC) {
			this.insertHandled(func, "Semantic error: Function name '" + func.name + "' already defined!");
			if (func.pars != null)
				func.pars.accept(this, pass);
			func.type.accept(this, pass);
		}
		else {
			symbTable.newScope();
			if (func.pars != null)
				func.pars.accept(this, pass);
			symbTable.newScope();
			if (func.defns != null)
				func.defns.accept(this, pass);
			if (func.stmt != null)
				func.stmt.accept(this, pass);
			symbTable.oldScope();
			symbTable.oldScope();
		}
		return null;
	}

	public Object visit(AstCallExpr call, Object pass) {
		AstDefn found = findHandled(call.name, call.location(), "Semantic error: Function name '" + call.name + "' not found!");
		SemAn.definedAt.put(call, found);
		if (call.args != null)
			call.args.accept(this, pass);
		return null;
	}

	private void doParams(AstFunDefn.AstParDefn param, Object pass) {
		if (pass == Pass.SECOND_FUNC) {
			this.insertHandled(param, "Semantic error: Parameter name '" + param.name + "' already defined!");
		}
		param.type.accept(this, pass);
	}

	@Override
	public Object visit(AstFunDefn.AstRefParDefn refPar, Object pass) {
		doParams(refPar, pass);
		return null;
	}

	@Override
	public Object visit(AstFunDefn.AstValParDefn valPar, Object pass) {
		doParams(valPar, pass);
		return null;
	}
}