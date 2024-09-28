package lang24.phase.seman;

import java.util.*;
import lang24.common.report.*;
import lang24.data.ast.tree.*;
import lang24.data.ast.tree.defn.*;
import lang24.data.ast.tree.expr.*;
import lang24.data.ast.tree.stmt.*;
import lang24.data.ast.tree.type.*;
import lang24.data.ast.tree.type.AstRecType.AstCmpDefn;
import lang24.data.ast.visitor.*;
import lang24.data.type.*;

/**
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class TypeResolver implements AstFullVisitor<SemType, Object> {

	/**
	 * Structural equivalence of types.
	 * 
	 * @param type1 The first type.
	 * @param type2 The second type.
	 * @return {@code true} if the types are structurally equivalent, {@code false}
	 *         otherwise.
	 */
	public static boolean equiv(SemType type1, SemType type2) {
		return equiv(type1, type2, new HashMap<SemType, HashSet<SemType>>());
	}

	/**
	 * Structural equivalence of types.
	 * 
	 * @param type1  The first type.
	 * @param type2  The second type.
	 * @param equivs Type synonyms assumed structurally equivalent.
	 * @return {@code true} if the types are structurally equivalent, {@code false}
	 *         otherwise.
	 */
	private static boolean equiv(SemType type1, SemType type2, HashMap<SemType, HashSet<SemType>> equivs) {

		if ((type1 instanceof SemNameType) && (type2 instanceof SemNameType)) {
			if (equivs == null)
				equivs = new HashMap<SemType, HashSet<SemType>>();

			if (equivs.get(type1) == null)
				equivs.put(type1, new HashSet<SemType>());
			if (equivs.get(type2) == null)
				equivs.put(type2, new HashSet<SemType>());
			if (equivs.get(type1).contains(type2) && equivs.get(type2).contains(type1))
				return true;
			else {
				HashSet<SemType> types;

				types = equivs.get(type1);
				types.add(type2);
				equivs.put(type1, types);

				types = equivs.get(type2);
				types.add(type1);
				equivs.put(type2, types);
			}
		}

		type1 = type1.actualType();
		type2 = type2.actualType();

		if (type1 instanceof SemVoidType)
			return (type2 instanceof SemVoidType);
		if (type1 instanceof SemBoolType)
			return (type2 instanceof SemBoolType);
		if (type1 instanceof SemCharType)
			return (type2 instanceof SemCharType);
		if (type1 instanceof SemIntType)
			return (type2 instanceof SemIntType);

		if (type1 instanceof SemArrayType) {
			if (!(type2 instanceof SemArrayType))
				return false;
			final SemArrayType arr1 = (SemArrayType) type1;
			final SemArrayType arr2 = (SemArrayType) type2;
			if (arr1.size != arr2.size)
				return false;
			return equiv(arr1.elemType, arr2.elemType, equivs);
		}

		if (type1 instanceof SemPointerType) {
			if (!(type2 instanceof SemPointerType))
				return false;
			final SemPointerType ptr1 = (SemPointerType) type1;
			final SemPointerType ptr2 = (SemPointerType) type2;
			if ((ptr1.baseType.actualType() instanceof SemVoidType)
					|| (ptr2.baseType.actualType() instanceof SemVoidType))
				return true;
			return equiv(ptr1.baseType, ptr2.baseType, equivs);
		}

		if (type1 instanceof SemStructType) {
			if (!(type2 instanceof SemStructType))
				return false;
			final SemStructType str1 = (SemStructType) type1;
			final SemStructType str2 = (SemStructType) type2;
			if (str1.cmpTypes.size() != str2.cmpTypes.size())
				return false;
			for (int c = 0; c < str1.cmpTypes.size(); c++)
				if (!(equiv(str1.cmpTypes.get(c), str2.cmpTypes.get(c), equivs)))
					return false;
			return true;
		}
		if (type1 instanceof SemUnionType) {
			if (!(type2 instanceof SemUnionType))
				return false;
			final SemUnionType uni1 = (SemUnionType) type1;
			final SemUnionType uni2 = (SemUnionType) type2;
			if (uni1.cmpTypes.size() != uni2.cmpTypes.size())
				return false;
			for (int c = 0; c < uni1.cmpTypes.size(); c++)
				if (!(equiv(uni1.cmpTypes.get(c), uni2.cmpTypes.get(c), equivs)))
					return false;
			return true;
		}

		throw new Report.InternalError();
	}

	// HELPERS

	private class UncheckedVoidPair {
		public AstNode defn;
		public SemType type;
		public UncheckedVoidPair(AstNode defn, SemType type) {
			this.defn = defn;
			this.type = type;
		}
	}

	HashMap<SemType, AstNodes<AstRecType.AstCmpDefn>> recordsToComponents = new HashMap<>();
	Stack<AstNode> definitionCycleStack = new Stack<>();
	Stack<SemType> functionReturnTypes = new Stack<>();
	ArrayList<UncheckedVoidPair> uncheckedVoidPairs = new ArrayList<>();

	private boolean testTypeArrayInclusion(SemType myType, SemType[] types) {
		boolean isOfType = false;
		for (SemType type : types) {
			if (equiv(myType, type)) isOfType = true;
		}
		return isOfType;
	}


	@Override
	public SemType visit(AstNodes<? extends AstNode> nodes, Object arg) {

		for (int i = 0; i < 4; i++) {
			if (i == 1) { // Check if any void type was used as struct/union component or array. 
				// This is because some components cannt be resolved during struct/union resolving. Applies to arrays also.
				for (UncheckedVoidPair pair : this.uncheckedVoidPairs) {
					if (equiv(pair.type, SemVoidType.type)) {
						if (pair.defn instanceof AstArrType) throw new Report.Error(pair.defn.location(), "Semantic error: Array cannot be of type void!");
						else throw new Report.Error(pair.defn.location(), "Semantic error: Cannot have void component in structs/unions!");
					}
				} 
				this.uncheckedVoidPairs.clear();
			}
			for (final AstNode node : nodes) {
				switch (i) {
					case 0:
						if (node instanceof AstTypDefn) node.accept(this, null);
						break;
					case 1:
						if (node instanceof AstVarDefn) node.accept(this, null);
						break;
					case 2:
						if (node instanceof AstFunDefn) node.accept(this, null);
						break;
					case 3:
						if (!(node instanceof AstTypDefn || node instanceof AstVarDefn || node instanceof AstFunDefn)) 
							node.accept(this, null);
						break;
					default:
						throw new Report.InternalError();
				}
			}
		}
		return null;
	}

	// TYPES

	@Override
	public SemType visit(AstAtomType atomType, Object arg) {
		if (atomType.type == AstAtomType.Type.BOOL) {
			SemAn.isType.put(atomType, SemBoolType.type);
			return SemBoolType.type;
		}
		else if (atomType.type == AstAtomType.Type.VOID) {
			SemAn.isType.put(atomType, SemVoidType.type);
			return SemVoidType.type;
		}
		else if (atomType.type == AstAtomType.Type.CHAR) {
			SemAn.isType.put(atomType, SemCharType.type);
			return SemCharType.type;
		}
		else if (atomType.type == AstAtomType.Type.INT) {
			SemAn.isType.put(atomType, SemIntType.type);
			return SemIntType.type;
		}
		else {
			throw new Report.Error(atomType.location(), "Semantic error: Unknown type!");
		}
	}

	// SINGLE DECLARATION WITH ISTYPE
	@Override
	public SemType visit(AstTypDefn typDefn, Object arg) {
		if (SemAn.isType.get(typDefn) == null) {
			definitionCycleStack.push(typDefn);
			SemNameType me = new SemNameType(typDefn.name);
			SemAn.isType.put(typDefn, me);
			SemType myType = typDefn.type.accept(this, arg);
			me.define(myType);
			definitionCycleStack.pop();
			return me;
		} else {
			return SemAn.isType.get(typDefn);
		}
	}

	@Override
	public SemType visit(AstNameType nameType, Object arg) {
		AstDefn defn = SemAn.definedAt.get(nameType);
		SemType myType = SemAn.isType.get(defn);
		if (definitionCycleStack.contains(defn) && arg == null || definitionCycleStack.contains(defn) && (definitionCycleStack.get(0) instanceof AstVarDefn))
			throw new Report.Error(nameType.location(), "Semantic error: Illegal recursive type definition detected! Use pointers instead!");
		if (myType == null) defn.accept(this, arg);
		myType = SemAn.isType.get(defn);
		if (myType == null) throw new Report.Error(nameType.location(), "Semantic error: Type '" + nameType.name + "' has not yet been defined!");
		SemAn.isType.put(nameType, myType);
		return myType;
	}
	// SINGLE DECLARATION WITH ISTYPE

	@Override
	public SemType visit(AstArrType arrType, Object arg) {
		SemType myType = arrType.elemType.accept(this, arg);

		arrType.size.accept(this, arg);
		long size = 0;
		
		try {
			size = Long.valueOf(((AstAtomExpr) arrType.size).value);
		} catch (Exception e) {
			throw new Report.Error(arrType.location(), "Semantic error: Cannot create array of given size!");
		}

		if (((myType instanceof SemNameType) || !equiv(myType, SemVoidType.type)) && 0 < size && size <= Math.pow(2, 63) - 1) {
			if (myType instanceof SemNameType) this.uncheckedVoidPairs.add(new UncheckedVoidPair(arrType, myType));
			SemType me = new SemArrayType(myType, size);
			SemAn.isType.put(arrType, me);
			return me;
		} else {
			throw new Report.Error(arrType.location(), "Semantic error: Illegal array definition! Check array type (cannot be void) or array size!");
		}
	}

	@Override
	public SemType visit(AstPtrType ptrType, Object arg) {
		SemType myType = ptrType.baseType.accept(this, "cameFromPointer");
		SemType me = new SemPointerType(myType);
		SemAn.isType.put(ptrType, me);
		return me;
	}

	private List<SemType> getComponentTypeList(AstNodes<AstCmpDefn> components, Object arg, String error) {
		List<SemType> myTypes = new LinkedList<>();
		HashSet<String> compNames = new HashSet<>();

		for (AstRecType.AstCmpDefn component : components) {
			if (compNames.contains(component.name)) 
				throw new Report.Error(component.location(), "Semantic error: '" + component.name + "' is a duplicate component name! Rename your components!");
			compNames.add(component.name);
			SemType compType = component.accept(this, arg);
			if ((compType instanceof SemNameType) || !equiv(compType, SemVoidType.type)) {
				if (compType instanceof SemNameType) this.uncheckedVoidPairs.add(new UncheckedVoidPair(component, compType));
				myTypes.add(compType);
			} else {
				throw new Report.Error(component.location(), error);
			}
		}
		return myTypes;
	}

	@Override
	public SemType visit(AstStrType strType, Object arg) {
		List<SemType> myTypes = getComponentTypeList(strType.cmps, arg, "Semantic error: Cannot have 'void' component in struct!");
		SemType me = new SemStructType(myTypes);
		recordsToComponents.put(me, strType.cmps);
		SemAn.isType.put(strType, me);
		return me;
	}

	@Override
	public SemType visit(AstUniType uniType, Object arg) {
		List<SemType> myTypes = getComponentTypeList(uniType.cmps, arg, "Semantic error: Cannot have 'void' component in union!");
		SemType me = new SemUnionType(myTypes);
		recordsToComponents.put(me, uniType.cmps);
		SemAn.isType.put(uniType, me);
		return me;
	}

	// DECLARATIONS

	@Override
	public SemType visit(AstVarDefn varDefn, Object arg) {
		if (SemAn.ofType.get(varDefn) == null) {
			definitionCycleStack.push(varDefn);
			SemType myType = varDefn.type.accept(this, arg);
			if (!equiv(myType, SemVoidType.type)) {
				SemAn.ofType.put(varDefn, myType);
			} else {
				throw new Report.Error(varDefn.location(), "Semantic error: Variable cannot be of type 'void'!");
			}
			definitionCycleStack.pop();
			return myType;
		} else {
			return SemAn.ofType.get(varDefn);
		}

	}

	@Override
	public SemType visit(AstFunDefn.AstValParDefn valParDefn, Object arg) {
		SemType myType = valParDefn.type.accept(this, arg);
		if (!equiv(myType, SemVoidType.type)) {
			SemAn.ofType.put(valParDefn, myType);
		} else {
			throw new Report.Error(valParDefn.location(), "Semantic error: Function pointer parameter cannot be of type 'void'!");
		}
		return myType;
	}

	@Override
	public SemType visit(AstFunDefn.AstRefParDefn refParDefn, Object arg) {
		SemType myType = refParDefn.type.accept(this, arg);
		if (!equiv(myType, SemVoidType.type)) {
			SemAn.ofType.put(refParDefn, myType);
		} else {
			throw new Report.Error(refParDefn.location(), "Semantic error: Function parameter cannot be of type 'void'!");
		}
		return myType;
	}

	@Override
	public SemType visit(AstRecType.AstCmpDefn cmpDefn, Object arg) {
		SemType myType = cmpDefn.type.accept(this, arg);
		
		if ((myType instanceof SemNameType) || !equiv(myType, SemVoidType.type)) {
			SemAn.ofType.put(cmpDefn, myType);
			return myType;
		} else {
			throw new Report.Error(cmpDefn.location(), "Semantic error: Component cannot be of type 'void'!");
		}
	}

	@Override
	public SemType visit(AstFunDefn funDefn, Object arg) {
		if (SemAn.ofType.get(funDefn) == null) {
			SemType myType = funDefn.type.accept(this, arg);
			SemType[] allowedReturnTypes = new SemType[] {SemVoidType.type, SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};
			if (!testTypeArrayInclusion(myType, allowedReturnTypes))
				throw new Report.Error(funDefn.type.location(), "Semantic error: Bad function return type. Use: 'void', 'int', 'char', 'bool' or pointers!");
			SemAn.ofType.put(funDefn, myType);

			if (funDefn.pars != null) {
				funDefn.pars.accept(this, arg);
				SemType[] allowedParamTypes = new SemType[] {SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};
				for (AstDefn param : funDefn.pars) {
					if (!testTypeArrayInclusion(SemAn.isType.get(param.type), allowedParamTypes))
						throw new Report.Error(param.location(), "Semantic error: Functions cannot have parameter types: 'void', 'array', 'strict', 'union'! Use pointers instead!");
				}
			}
			if (funDefn.defns != null)
				funDefn.defns.accept(this, arg);
			functionReturnTypes.push(myType);
			if (funDefn.stmt != null)
				funDefn.stmt.accept(this, arg);
			functionReturnTypes.pop();

			if (funDefn.stmt == null || equiv(SemAn.ofType.get(funDefn.stmt), SemVoidType.type)) {
				return myType;
			} else 
				throw new Report.Error(funDefn.location(), "Semantic error: Illegal function definition!");
		} else {
			return SemAn.ofType.get(funDefn);
		}
	}

	// EXPRESSIONS

	@Override
	public SemType visit(AstNameExpr nameExpr, Object arg) {
		AstDefn defn = SemAn.definedAt.get(nameExpr);
		if (!(defn instanceof AstVarDefn || defn instanceof AstFunDefn.AstParDefn))
			throw new Report.Error(nameExpr.location(), "Semantic error: '"+ defn.name +"' must be either variable or parameter!");
		SemType myType = SemAn.ofType.get(defn);
		if (myType == null) defn.accept(this, arg);
		myType = SemAn.ofType.get(defn);
		if (myType == null) throw new Report.Error(nameExpr.location(), "Semantic error: Variable '" + nameExpr.name + "' has not yet been defined!");
		if (!equiv(myType, SemVoidType.type)) {
			SemAn.ofType.put(nameExpr, myType);
			return myType;
		} else
			throw new Report.Error(defn.location(), "Semantic error: A variable (" + defn.name + ") cannot be of type 'void'!");

	}

	@Override
	public SemType visit(AstAtomExpr atomExpr, Object arg) {
		if (atomExpr.type == AstAtomExpr.Type.VOID) {
			SemType myType = SemVoidType.type;
			SemAn.ofType.put(atomExpr, myType);
			return myType;
		}
		else if (atomExpr.type == AstAtomExpr.Type.BOOL) {
			SemType myType = SemBoolType.type;
			SemAn.ofType.put(atomExpr, myType);
			return myType;
		}
		else if (atomExpr.type == AstAtomExpr.Type.CHAR) {
			SemType myType = SemCharType.type;
			SemAn.ofType.put(atomExpr, myType);
			return myType;
		}
		else if (atomExpr.type == AstAtomExpr.Type.INT) {
			SemType myType = SemIntType.type;
			SemAn.ofType.put(atomExpr, myType);
			return myType;
		}
		else if (atomExpr.type == AstAtomExpr.Type.STR) {
			SemType myType = SemCharType.type;
			SemType me = new SemPointerType(myType);
			SemAn.ofType.put(atomExpr, me);
			return me;
		}
		else if (atomExpr.type == AstAtomExpr.Type.PTR) {
			SemType myType = SemVoidType.type;
			SemType me = new SemPointerType(myType);
			SemAn.ofType.put(atomExpr, me);
			return me;
		}
		else {
			throw new Report.Error(atomExpr.location(), "Semantic error: Unknown constant type!");
		}
	}

	@Override
	public SemType visit(AstPfxExpr pfxExpr, Object arg) {
		pfxExpr.expr.accept(this, arg);
		if (pfxExpr.oper == AstPfxExpr.Oper.NOT) {
			SemType myType = SemBoolType.type;
			if (equiv(SemAn.ofType.get(pfxExpr.expr), myType)) {
				SemAn.ofType.put(pfxExpr, myType);
				return myType;
			} else
				throw new Report.Error(pfxExpr.expr.location(), "Semantic error: Expression must be of type 'bool'!");
		}
		else if (pfxExpr.oper == AstPfxExpr.Oper.ADD || pfxExpr.oper == AstPfxExpr.Oper.SUB) {
			SemType myType = SemIntType.type;
			if (equiv(SemAn.ofType.get(pfxExpr.expr), myType)) {
				SemAn.ofType.put(pfxExpr, myType);
				return myType;
			} else
				throw new Report.Error(pfxExpr.expr.location(), "Semantic error: Expression must be of type 'int'!");
		}else if (pfxExpr.oper == AstPfxExpr.Oper.PTR) {
			SemType type = SemAn.ofType.get(pfxExpr.expr);
			SemType myType = new SemPointerType(type);
			if (SemAn.isLVal.get(pfxExpr.expr)) {
				SemAn.ofType.put(pfxExpr, myType);
				return myType;
			} else
				throw new Report.Error(pfxExpr.expr.location(), "Semantic error: Expression in a pointer must be a LVALUE!");
		} else
			throw new Report.Error(pfxExpr.expr.location(), "Semantic error: Unknown prefix applied to expression!");
	}

	@Override
	public SemType visit(AstBinExpr binExpr, Object arg) {
		SemType exp1Type = binExpr.fstExpr.accept(this, arg);
		SemType exp2Type = binExpr.sndExpr.accept(this, arg);

		if (binExpr.oper == AstBinExpr.Oper.AND || binExpr.oper == AstBinExpr.Oper.OR) {
			if (equiv(exp1Type, SemBoolType.type) && equiv(exp2Type, SemBoolType.type)) {
				SemAn.ofType.put(binExpr, SemBoolType.type);
				return SemBoolType.type;
			} else 
				throw new Report.Error(binExpr.location(), "Semantic erorr: Both sub-expressions must be of type 'bool' to use in AND or OR expression!");
		} 
		else if (binExpr.oper == AstBinExpr.Oper.EQU || binExpr.oper == AstBinExpr.Oper.NEQ) {
			SemType[] allowedTypes = new SemType[] {SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};
			if (testTypeArrayInclusion(exp1Type, allowedTypes) && testTypeArrayInclusion(exp2Type, allowedTypes) 
															   && equiv(exp1Type, exp2Type)) {
				SemAn.ofType.put(binExpr, SemBoolType.type);
				return SemBoolType.type;
			} else
				throw new Report.Error(binExpr.location(), "Semantic erorr: Both sub-expressions must be of type: 'bool', 'char', 'int' or a pointer and they must be of the same type to use in '==' or '!=' expressions!");
		} 
		else if (binExpr.oper == AstBinExpr.Oper.LTH || binExpr.oper == AstBinExpr.Oper.GTH ||
				   binExpr.oper == AstBinExpr.Oper.LEQ || binExpr.oper == AstBinExpr.Oper.GEQ) {
			SemType[] allowedTypes = new SemType[] {SemIntType.type, SemCharType.type, SemPointerType.type};
			if (testTypeArrayInclusion(exp1Type, allowedTypes) && testTypeArrayInclusion(exp2Type, allowedTypes) 
																&& equiv(exp1Type, exp2Type)) {
				SemAn.ofType.put(binExpr, SemBoolType.type);
				return SemBoolType.type;
			} else
				throw new Report.Error(binExpr.location(), "Semantic erorr: Both sub-expressions must be of type: 'char', 'int' or a pointer and they must be of the same type to use in '<', '>', '<=' or '>=' expressions!");
		} 
		else if (binExpr.oper == AstBinExpr.Oper.ADD || binExpr.oper == AstBinExpr.Oper.SUB ||
				   binExpr.oper == AstBinExpr.Oper.DIV || binExpr.oper == AstBinExpr.Oper.MUL ||
				   binExpr.oper == AstBinExpr.Oper.MOD) {
			if (equiv(exp1Type, SemIntType.type) && equiv(exp2Type, SemIntType.type)) {
				SemAn.ofType.put(binExpr, SemIntType.type);
				return SemIntType.type;
			} else
				throw new Report.Error(binExpr.location(), "Semantic erorr: Both sub-expressions must be of type 'int' to use in arithmetic expressions!");
		}
		else
			throw new Report.Error(binExpr.location(), "Semantic error: Unknown binary operation!");
	}

	@Override
	public SemType visit(AstSfxExpr sfxExpr, Object arg) {
		SemType type = sfxExpr.expr.accept(this, arg).actualType();

		if (AstSfxExpr.Oper.PTR == sfxExpr.oper) {
			if (equiv(type, SemPointerType.type)) {
				SemType myType = ((SemPointerType) type).baseType;
				SemAn.ofType.put(sfxExpr, myType);
				return myType;
			} else
				throw new Report.Error(sfxExpr.expr.location(), "Semantic error: Expression is not pointer! Cannot dereference it!");
		} else
			throw new Report.Error(sfxExpr.location(), "Semantic error: Unknown sufix operator!");
	}

	@Override
	public SemType visit(AstArrExpr arrExpr, Object arg) {
		SemType arr = arrExpr.arr.accept(this, arg).actualType();
		SemType index = arrExpr.idx.accept(this, arg).actualType();

		if (arr instanceof SemArrayType) {
			if (equiv(index, SemIntType.type)) {
				if (SemAn.isLVal.get(arrExpr.arr)) {
					SemAn.ofType.put(arrExpr, ((SemArrayType) arr).elemType);
					return ((SemArrayType) arr).elemType;
				} else 
					throw new Report.Error(arrExpr.arr.location(), "Semantic error: Array must be a LVALUE!");
			} else
				throw new Report.Error(arrExpr.location(), "Semantic error: Array access index must be of type 'int'!");
		} else
			throw new Report.Error(arrExpr.location(), "Semantic error: Cannot do array access on non-array type!");
	}

	@Override
	public SemType visit(AstCmpExpr cmpExpr, Object arg) {
		SemType container = cmpExpr.expr.accept(this, arg).actualType();
		
		if (container instanceof SemStructType || container instanceof SemUnionType) {
			AstNodes<AstRecType.AstCmpDefn> components = recordsToComponents.get(container);
			for (AstRecType.AstCmpDefn component : components) {
				if (component.name.equals(cmpExpr.name)) {
					SemType myType = SemAn.isType.get(component.type);
					SemAn.ofType.put(cmpExpr, myType);
					SemAn.definedAt.put(cmpExpr, component);
					return myType;
				}
			}
			throw new Report.Error(cmpExpr.location(), "Semantic error: Unknown struct/union component!");
		}
		else 
			throw new Report.Error(cmpExpr.location(), "Semantic error: Cannot access component of non-(struct/union) type!");
	}

	@Override
	public SemType visit(AstCallExpr callExpr, Object arg) {
		AstFunDefn defn = null;
		try {
			defn = (AstFunDefn) SemAn.definedAt.get(callExpr);
		} catch (Exception e) {
			throw new Report.Error(callExpr.location(), "Semantic error: '" + callExpr.name + "' is not a function, cannot call it!");
		}
		SemType returnType = SemAn.ofType.get(defn);
		if (returnType == null) defn.accept(this, arg);
		returnType = SemAn.ofType.get(defn);
		if (returnType == null) throw new Report.Error(callExpr.location(), "Semantic error: Function '" + callExpr.name + "' is not defined!");

		SemType[] allowedReturnTypes = new SemType[] {SemVoidType.type, SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};
		if (!testTypeArrayInclusion(returnType, allowedReturnTypes))
			throw new Report.Error(defn.type.location(), "Semantic error: Illegal function return type! Check your types!");

		if (callExpr.args == null && defn.pars == null) {
			SemAn.ofType.put(callExpr, returnType);
			return returnType;
		} 
		else if (callExpr.args != null && defn.pars != null && callExpr.args.size() == defn.pars.size()) {
			for (int index = 0; index < defn.pars.size(); index++) {
				AstFunDefn.AstParDefn param = defn.pars.get(index);
				AstExpr expr = callExpr.args.get(index);

				SemType paramType = SemAn.ofType.get(param);
				SemType exprType = expr.accept(this, arg);

				if (!equiv(paramType, exprType)) 
					throw new Report.Error(callExpr.location(), "Semantic error: " + index + "th function parameter type and argument type do not match! Check your types!");
				
				SemType[] allowedArgumentTypes = new SemType[] {SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};

				if (!testTypeArrayInclusion(exprType, allowedArgumentTypes))
					throw new Report.Error(expr.location(), "Semantic error: Illegal argument type! Check your types!");

				if (param instanceof AstFunDefn.AstRefParDefn && !SemAn.isLVal.get(expr))
					throw new Report.Error(expr.location(), "Semantic error: Function refrence parameter got argument which is not LVALUE!");
			}
			SemAn.ofType.put(callExpr, returnType);
			return returnType;
		}
		else 
			throw new Report.Error(callExpr.location(), "Semantic error: Function call arguments do not match function definition parameters!");
	}

	@Override
	public SemType visit(AstCastExpr castExpr, Object arg) {
		SemType typeType = castExpr.type.accept(this, arg);
		SemType expType = castExpr.expr.accept(this, arg);

		SemType[] allowedTypes = new SemType[] {SemIntType.type, SemCharType.type, SemPointerType.type};

		if (testTypeArrayInclusion(typeType, allowedTypes) && testTypeArrayInclusion(expType, allowedTypes)) {
			SemAn.ofType.put(castExpr, typeType);
			return typeType;
		} else 
			throw new Report.Error(castExpr.location(), "Semantic error: Cannot cast with given types! Check cast types!");
	}

	@Override
	public SemType visit(AstSizeofExpr sizeofExpr, Object arg) {
		SemType myType = sizeofExpr.type.accept(this, arg);
		SemAn.ofType.put(sizeofExpr, SemIntType.type);
		return SemIntType.type;
	}

	// STATEMENTS

	@Override
	public SemType visit(AstAssignStmt assignStmt, Object arg) {
		SemType exp1Type = assignStmt.dst.accept(this, arg).actualType();
		SemType exp2Type = assignStmt.src.accept(this, arg).actualType();

		SemType[] allowedTypes = new SemType[] {SemIntType.type, SemCharType.type, SemBoolType.type, SemPointerType.type};
		if (testTypeArrayInclusion(exp1Type, allowedTypes) && testTypeArrayInclusion(exp2Type, allowedTypes) 
			&& equiv(exp1Type, exp2Type) && SemAn.isLVal.get(assignStmt.dst)) {
			SemAn.ofType.put(assignStmt, SemVoidType.type);
			return SemVoidType.type;
		} else 
			throw new Report.Error(assignStmt.location(), "Semantic error: Destination and source expression of assign statement do not match!");
	}

	@Override
	public SemType visit(AstExprStmt exprStmt, Object arg) {
		SemType type = exprStmt.expr.accept(this, arg);

		if (equiv(type, SemVoidType.type)) {
			SemAn.ofType.put(exprStmt, SemVoidType.type);
			return SemVoidType.type;
		} else
			throw new Report.Error(exprStmt.location(), "Semantic error: Each expression must be of type void!");
	}

	@Override
	public SemType visit(AstIfStmt ifStmt, Object arg) {
		SemType cond = ifStmt.cond.accept(this, arg);
		ifStmt.thenStmt.accept(this, arg);
	
		if (ifStmt.elseStmt != null) {
			ifStmt.elseStmt.accept(this, arg);
			if (equiv(cond, SemBoolType.type)) {
				SemAn.ofType.put(ifStmt, SemVoidType.type);
				return SemVoidType.type;
			} else 
				throw new Report.Error(ifStmt.cond.location(), "Semantic error: If condition is not boolean! Check your condition!");
		} 
		else {
			if (equiv(cond, SemBoolType.type)) {
				SemAn.ofType.put(ifStmt, SemVoidType.type);
				return SemVoidType.type;
			} else 
				throw new Report.Error(ifStmt.cond.location(), "Semantic error: If condition is not boolean! Check your condition!");
		}
	}

	@Override
	public SemType visit(AstWhileStmt whileStmt, Object arg) {
		SemType cond = whileStmt.cond.accept(this, arg);
		whileStmt.stmt.accept(this, arg);
		if (equiv(cond, SemBoolType.type)) {
			SemAn.ofType.put(whileStmt, SemVoidType.type);
			return SemVoidType.type;
		} else 
			throw new Report.Error(whileStmt.cond.location(), "Semantic error: While condition is not boolean! Check your condition!");
	}

	@Override
	public SemType visit(AstBlockStmt blockStmt, Object arg) {
		if (blockStmt.stmts != null) {
			blockStmt.stmts.accept(this, arg);
			SemAn.ofType.put(blockStmt, SemVoidType.type);
			return SemVoidType.type;
		}
		else 
			throw new Report.Error(blockStmt.location(), "Semantic error: Block of statements cannot be empty!");
	}

	@Override
	public SemType visit(AstReturnStmt retStmt, Object arg) {
		SemType returnType = retStmt.expr.accept(this, arg);
		if (equiv(returnType, functionReturnTypes.peek())) {
			SemAn.ofType.put(retStmt, SemVoidType.type);
			return SemVoidType.type;
		} else
			throw new Report.Error(retStmt.location(), "Semantic error: Function return statement type doesnt match actual function return type! Check your types!");
	}


}