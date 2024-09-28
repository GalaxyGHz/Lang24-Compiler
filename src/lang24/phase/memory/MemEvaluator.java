package lang24.phase.memory;

import java.util.*;

import lang24.data.ast.tree.*;
import lang24.data.ast.tree.defn.*;
import lang24.data.ast.tree.defn.AstFunDefn.AstParDefn;
import lang24.data.ast.tree.expr.AstAtomExpr;
import lang24.data.ast.tree.expr.AstCallExpr;
import lang24.data.ast.tree.type.AstRecType;
import lang24.data.ast.tree.type.AstRecType.AstCmpDefn;
import lang24.data.ast.tree.type.AstStrType;
import lang24.data.ast.tree.type.AstUniType;
import lang24.data.ast.visitor.*;
import lang24.data.mem.*;
import lang24.data.type.*;
import lang24.data.type.visitor.*;
import lang24.phase.seman.SemAn;

/**
 * Computing memory layout: stack frames and variable accesses.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class MemEvaluator implements AstFullVisitor<Object, Object> {

    private long depth = -1;
    private HashMap<AstFunDefn, HashSet<Long>> mapFunctionsToCallArguments = new HashMap<>();

    public static long getTypeSize(SemType type) {
        type = type.actualType();
        if (type instanceof SemVoidType) return 0;
        else if (type instanceof SemCharType) return 8;
        else if (type instanceof SemBoolType) return 8;
        else if (type instanceof SemPointerType) return 8;
        else if (type instanceof SemIntType) return 8;
        else if (type instanceof SemArrayType) {
            SemArrayType array = (SemArrayType) type;
            long size = getTypeSize(array.elemType);
            if (size % 8 != 0) size += 8 - (size % 8);
            return size * array.size;
        }
        else if (type instanceof SemStructType) {
            SemStructType struct = (SemStructType) type;
            long size = 0;
            for (SemType subType : struct.cmpTypes) {
                size += getTypeSize(subType);
                if (size % 8 != 0) size += 8 - (size % 8);
            }
            return size;
        }
        else if (type instanceof SemUnionType) {
            SemUnionType union = (SemUnionType) type;
            long size = 0;
            for (SemType subType : union.cmpTypes) {
                long newSize = getTypeSize(subType);
                if (newSize > size) size = newSize;
            }
            return size;
        }
        else {
            System.out.println(type);
            throw new InternalError("Memory calculation error: Cannot calculate size of a type! Check MemE");
        }
        
    }


    @Override
	public Object visit(AstNodes<? extends AstNode> nodes, Object arg) {
        if (nodes.get(0) instanceof AstCmpDefn) {
            long offset = 0;
            for (final AstNode node : nodes) {
                long size = (long) node.accept(this, offset);
                if (arg.equals("struct")) {
                    offset += size;
                    if (offset % 8 != 0) offset += 8 - (offset % 8);
                }
            }
            return null;
        }
        else if (nodes.get(0) instanceof AstParDefn) {
            long offset = 8;
            for (final AstNode node : nodes) {
                long size = (long) node.accept(this, offset);
                offset += size;
                if (offset % 8 != 0) offset += 8 - (offset % 8);
            }
            return null;
        }
        else if (nodes.get(0) instanceof AstDefn) {
            long offset = 0;
            for (final AstNode node : nodes) {
                long mySize = (long) node.accept(this, offset); // It needs to be negative, handleled in VarDefn
                offset += mySize;
                if (offset % 8 != 0) {
                    offset += 8 - (offset % 8);
                }
            }
            return offset;
        }
        else {
            for (final AstNode node : nodes)
                node.accept(this, arg);
            return null;
        }
	}


    @Override
	public Object visit(AstStrType strType, Object arg) {
		strType.cmps.accept(this, "struct");
		return null;
	}

	@Override
	public Object visit(AstUniType uniType, Object arg) {
		uniType.cmps.accept(this, "union");
		return null;
	}


    @Override
	public Object visit(AstCmpDefn cmpDefn, Object arg) {
		cmpDefn.type.accept(this, arg);
        SemType myType = SemAn.ofType.get(cmpDefn).actualType();
        long mySize = getTypeSize(myType);
        MemRelAccess mem = new MemRelAccess(mySize, (long) arg, -1);
        Memory.cmpAccesses.put(cmpDefn, mem);
		return mySize;
	}

    private String convertHEX(String input) {
        input = input.substring(1, input.length() - 1);
        StringBuilder out = new StringBuilder();
        out.append('"');
        String escape = "";
        for (Character c : input.toCharArray()) {
            if (escape.length() == 0 && c == '\\')
                escape += c;
            else if (escape.equals("\\") && c == '\\') {
                escape = "";
                out.append('\\');
            } else if (escape.equals("\\") && c == '"') {
                escape = "";
                out.append('"');
            } else if (escape.equals("\\") && c == 'n') {
                escape = "";
                out.append('\n');
            } else if (escape.length() >= 1 && "ABCDEF0123456789".contains(c.toString())) {
                escape += c;
                if (escape.length() == 3) {
                    out.append((char) (Integer.parseInt(escape.substring(1), 16)));
                    escape = "";
                }
            } else {
                out.append(c);
            }
        }
        out.append((char) 0);
        out.append('"');
        return out.toString();
    }


	@Override
	public Object visit(AstAtomExpr atomExpr, Object arg) {
        SemType myType = SemAn.ofType.get(atomExpr).actualType();
        if (atomExpr.type == AstAtomExpr.Type.STR) {
            String newString = atomExpr.value;
            newString = convertHEX(newString);
            // We have null byte at end thats why its 1 more than length, minus the quotes
            MemAbsAccess mem = new MemAbsAccess((newString.length() - 2)*8, new MemLabel(), newString); 
            Memory.strings.put(atomExpr, mem);
        }
		return null;
	}

	@Override
	public Object visit(AstVarDefn varDefn, Object arg) {
        SemType myType = SemAn.ofType.get(varDefn).actualType();
        long mySize = getTypeSize(myType);
        MemAccess mem = null;
        if (depth == -1)
            mem = new MemAbsAccess(mySize, new MemLabel(varDefn.name));
        else {
            long myOffset = (long) arg;
            myOffset += mySize;
            if (myOffset % 8 != 0) {
                myOffset += 8 - (myOffset % 8);
            } 
            mem = new MemRelAccess(mySize, (long) -myOffset, depth);
        }
            
        Memory.varAccesses.put(varDefn, mem);
        varDefn.type.accept(this, arg);
        return mySize;
	}

    @Override
	public Object visit(AstCallExpr callExpr, Object arg) {
		if (callExpr.args != null) {
			callExpr.args.accept(this, arg);
            mapFunctionsToCallArguments.get(arg).add(((long) callExpr.args.size())*8);
        }
        else mapFunctionsToCallArguments.get(arg).add((long) 0);
		return null;
	}

	@Override
	public Object visit(AstFunDefn funDefn, Object arg) {
        depth += 1;
        long locsSize = 0;
        long argsSize = 0;
		if (funDefn.pars != null)
			funDefn.pars.accept(this, arg);
        mapFunctionsToCallArguments.put(funDefn, new HashSet<Long>());
		if (funDefn.stmt != null)
			funDefn.stmt.accept(this, funDefn);
        if (mapFunctionsToCallArguments.get(funDefn).size() == 0) argsSize = 0;
        else argsSize = Collections.max(mapFunctionsToCallArguments.get(funDefn)) + 8; // + 8 for static link/return
		if (funDefn.defns != null)
            locsSize = (long) funDefn.defns.accept(this, arg);
		funDefn.type.accept(this, arg);

        long overallSize = locsSize + 8 + 8 + argsSize; // locals + old FP + return address + arguments

        if (funDefn.stmt != null) {
            MemFrame mem = null;
            if (depth == 0)
                mem = new MemFrame(new MemLabel(funDefn.name), depth, locsSize, argsSize, overallSize);
            else 
                mem = new MemFrame(new MemLabel(), depth, locsSize, argsSize, overallSize);
            Memory.frames.put(funDefn, mem);
        }
        depth -= 1;
		return (long) 0;
	}

    @Override
	public Object visit(AstFunDefn.AstRefParDefn refParDefn, Object arg) {
        SemType myType = SemAn.ofType.get(refParDefn).actualType();
        long size = getTypeSize(myType);
        MemRelAccess mem = new MemRelAccess(size, (long) arg, depth);
        Memory.parAccesses.put(refParDefn, mem);

		refParDefn.type.accept(this, arg);
		return size;
	}

	@Override
	public Object visit(AstFunDefn.AstValParDefn valParDefn, Object arg) {
        SemType myType = SemAn.ofType.get(valParDefn).actualType();
        long size = getTypeSize(myType);
        MemRelAccess mem = new MemRelAccess(size, (long) arg, depth);
        Memory.parAccesses.put(valParDefn, mem);

		valParDefn.type.accept(this, arg);
		return size;
	}

    @Override
	public Object visit(AstTypDefn typDefn, Object arg) {
		typDefn.type.accept(this, arg);
		return (long) 0;
	}



}
