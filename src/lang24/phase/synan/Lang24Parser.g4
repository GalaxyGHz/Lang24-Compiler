parser grammar Lang24Parser;

@header {

	package lang24.phase.synan;
	
	import java.util.*;
	import lang24.common.report.*;
	import lang24.data.token.*;
	import lang24.phase.lexan.*;
	import lang24.data.ast.tree.*;
	import lang24.data.ast.tree.defn.*;
	import lang24.data.ast.tree.type.*;
	import lang24.data.ast.tree.expr.*;
	import lang24.data.ast.tree.stmt.*;

}

@members {

	private Location loc(Token tok) { return new Location((LocLogToken)tok); }
	private Location loc(Token     tok1, Token     tok2) { return new Location((LocLogToken)tok1, (LocLogToken)tok2); }
	private Location loc(Token     tok1, Locatable loc2) { return new Location((LocLogToken)tok1, loc2); }
	private Location loc(Locatable loc1, Token     tok2) { return new Location(loc1, (LocLogToken)tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }

}

options{
    tokenVocab=Lang24Lexer;
}

source returns [AstNode ast] : definitions EOF {$ast = new AstNodes<AstDefn>($definitions.list);} ;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type, variable and function definitions!");
	}

definitions returns [List list]
	@init {$list = new ArrayList<AstDefn>();}
	: definition {$list.add($definition.ast);}
	| definition d=definitions {$list.add($definition.ast); $list.addAll($d.list);}
	; 
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type, variable and function definitions!");
	}
	
definition returns [AstNode ast]
	: type_definition {$ast = $type_definition.ast;}
	| variable_definition {$ast = $variable_definition.ast;}
	| function_definition {$ast = $function_definition.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type, variable and function definitions!");
	}


type_definition returns [AstNode ast]
	: IDENTIFIER ASSIGN type {$ast = new AstTypDefn(loc($IDENTIFIER, $type.ast.location()), $IDENTIFIER.text, $type.ast);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type definitions!");
	}

variable_definition returns [AstNode ast]
	: IDENTIFIER COLON type {$ast = new AstVarDefn(loc($IDENTIFIER, $type.ast.location()), $IDENTIFIER.text, $type.ast);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your variable definitions!");
	}

function_definition returns [AstNode ast]
	: IDENTIFIER LEFTPAREN (parameters)? RIGHTPAREN COLON type (ASSIGN statement (LEFTBRACE definitions RIGHTBRACE)? )? {

		AstNodes<AstFunDefn.AstParDefn> params;
		if ($parameters.ctx != null) params = new AstNodes<AstFunDefn.AstParDefn>($parameters.list);
		//else params = new AstNodes<AstFunDefn.AstParDefn>(new ArrayList<>());
		else params = null;

		AstNodes<AstDefn> defs;
		if ($definitions.ctx != null) defs = new AstNodes<AstDefn>($definitions.list);
		//else defs = new AstNodes<AstDefn>(new ArrayList<>());
		else defs = null;

		if ($RIGHTBRACE != null) {
			$ast = new AstFunDefn(loc($IDENTIFIER, $RIGHTBRACE), $IDENTIFIER.text, params, $type.ast, $statement.ast, defs);
		} else if ($statement.ctx != null) {
			$ast = new AstFunDefn(loc($IDENTIFIER, $statement.ast.location()), $IDENTIFIER.text, params, $type.ast, $statement.ast, defs);
		} else {
			$ast = new AstFunDefn(loc($IDENTIFIER, $type.ast.location()), $IDENTIFIER.text, params, $type.ast, null, defs);
		}
	}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your function definitions!");
	}

parameters returns [List list]
	@init {$list = new ArrayList<AstFunDefn.AstRefParDefn>();}
	: parameter COMMA p=parameters {$list.add($parameter.ast); $list.addAll($p.list);}
	| parameter {$list.add($parameter.ast);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your function parameters!");
	}

parameter returns [AstNode ast]
	: CARET IDENTIFIER COLON type {$ast = new AstFunDefn.AstRefParDefn(loc($CARET, $type.ast.location()), $IDENTIFIER.text, $type.ast);}
	| IDENTIFIER COLON type {$ast = new AstFunDefn.AstValParDefn(loc($IDENTIFIER, $type.ast.location()), $IDENTIFIER.text, $type.ast);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your function parameters!");
	}

statement returns [AstStmt ast]
	: expression SEMICOLON {$ast = new AstExprStmt(loc($expression.ast.location(), $SEMICOLON), $expression.ast);}
	| e1=expression ASSIGN e2=expression SEMICOLON {$ast = new AstAssignStmt(loc($e1.ast.location(), $SEMICOLON), $e1.ast, $e2.ast);}
	| IF expression THEN s1=statement (ELSE s2=statement)? {
		if ($s2.ctx != null) {
			$ast = new AstIfStmt(loc($IF, $s2.ast.location()), $expression.ast, $s1.ast, $s2.ast);
		} else {
			$ast = new AstIfStmt(loc($IF, $s1.ast.location()), $expression.ast, $s1.ast, null);
		}
	}
	| WHILE expression COLON s=statement {$ast = new AstWhileStmt(loc($WHILE, $s.ast.location()), $expression.ast, $s.ast);}
	| RETURN expression SEMICOLON {$ast = new AstReturnStmt(loc($RETURN, $SEMICOLON), $expression.ast);}
	| LEFTBRACE ss=statements RIGHTBRACE {$ast = new AstBlockStmt(loc($LEFTBRACE, $RIGHTBRACE), $ss.list);}
	; 
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your statements!");
	}

statements returns [List list]
	@init {$list = new ArrayList<AstStmt>();}
	: statement {$list.add($statement.ast);}
	| statement s=statements {$list.add($statement.ast); $list.addAll($s.list);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your statements!");
	}

type returns [AstType ast] 
	: VOID {$ast = new AstAtomType(loc($VOID), AstAtomType.Type.VOID);}
	| BOOL {$ast = new AstAtomType(loc($BOOL), AstAtomType.Type.BOOL);}
	| CHAR {$ast = new AstAtomType(loc($CHAR), AstAtomType.Type.CHAR);}
	| INT {$ast = new AstAtomType(loc($INT), AstAtomType.Type.INT);}
	| LEFTBRACKET INTCONST RIGHTBRACKET t=type {$ast = new AstArrType(loc($LEFTBRACKET, $t.ast.location()), 
																	$t.ast, 
																	new AstAtomExpr(loc($INTCONST),
																					AstAtomExpr.Type.INT, 
																					$INTCONST.text));}
	| CARET t=type {$ast = new AstPtrType(loc($CARET, $t.ast.location()), $t.ast);}
	| LEFTPAREN components RIGHTPAREN {$ast = new AstStrType(loc($LEFTPAREN, $RIGHTPAREN), new AstNodes<AstRecType.AstCmpDefn>($components.list));}
	| LEFTBRACE components RIGHTBRACE {$ast = new AstUniType(loc($LEFTBRACE, $RIGHTBRACE), new AstNodes<AstRecType.AstCmpDefn>($components.list));}
	| IDENTIFIER {$ast = new AstNameType(loc($IDENTIFIER), $IDENTIFIER.text);}
	; 
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your types!");
	}

components returns [List list]
	@init {$list = new ArrayList<AstRecType.AstCmpDefn>();}
	: component {$list.add($component.ast);}
	| component COMMA c=components {$list.add($component.ast); $list.addAll($c.list);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type components!");
	}
component returns [AstNode ast]
	: IDENTIFIER COLON type {$ast = new AstRecType.AstCmpDefn(loc($IDENTIFIER, $type.ast.location()), $IDENTIFIER.text, $type.ast);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your type components!");
	}

expression returns [AstExpr ast] 
	: e=expression OR conjunctive {
		$ast = new AstBinExpr(loc($e.ast.location(), $conjunctive.ast.location()), AstBinExpr.Oper.OR, $e.ast, $conjunctive.ast);
	}
	| conjunctive {$ast = $conjunctive.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your OR expressions!");
	}

conjunctive returns [AstExpr ast]
	: c=conjunctive AND relational {
		$ast = new AstBinExpr(loc($c.ast.location(), $relational.ast.location()), AstBinExpr.Oper.AND, $c.ast, $relational.ast);
	}
	| relational {$ast = $relational.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your AND expressions!");
	}

relational returns [AstExpr ast]
	: r=relational EQUAL additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.EQU, $r.ast, $additive.ast);}
	| r=relational NOTEQUAL additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.NEQ, $r.ast, $additive.ast);}
	| r=relational LESS additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.LTH, $r.ast, $additive.ast);} 
	| r=relational GREATER additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.GTH, $r.ast, $additive.ast);}
	| r=relational LESSEQUAL additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.LEQ, $r.ast, $additive.ast);}
	| r=relational GREATEREQUAL additive {$ast = new AstBinExpr(loc($r.ast.location(), $additive.ast.location()), AstBinExpr.Oper.GEQ, $r.ast, $additive.ast);}
	| additive {$ast = $additive.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your relational expressions!");
	}

additive returns [AstExpr ast]
	: a=additive PLUS multiplicative {
		$ast = new AstBinExpr(loc($a.ast.location(), $multiplicative.ast.location()), AstBinExpr.Oper.ADD, $a.ast, $multiplicative.ast);
	}
	| a=additive MINUS multiplicative {
		$ast = new AstBinExpr(loc($a.ast.location(), $multiplicative.ast.location()), AstBinExpr.Oper.SUB, $a.ast, $multiplicative.ast);
	}
	| multiplicative {$ast = $multiplicative.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your additive expressions!");
	}

multiplicative returns [AstExpr ast]
	: m=multiplicative STAR prefix {$ast = new AstBinExpr(loc($m.ast.location(), $prefix.ast.location()), AstBinExpr.Oper.MUL, $m.ast, $prefix.ast);}
	| m=multiplicative DIV prefix {$ast = new AstBinExpr(loc($m.ast.location(), $prefix.ast.location()), AstBinExpr.Oper.DIV, $m.ast, $prefix.ast);}
	| m=multiplicative MOD prefix {$ast = new AstBinExpr(loc($m.ast.location(), $prefix.ast.location()), AstBinExpr.Oper.MOD, $m.ast, $prefix.ast);}
	| prefix {$ast = $prefix.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your multiplicative expressions!");
	}

prefix returns [AstExpr ast]
	: NOT p=prefix {$ast = new AstPfxExpr(loc($NOT, $p.ast.location()), AstPfxExpr.Oper.NOT, $p.ast);}
	| PLUS p=prefix {$ast = new AstPfxExpr(loc($PLUS, $p.ast.location()), AstPfxExpr.Oper.ADD, $p.ast);}
	| MINUS p=prefix {$ast = new AstPfxExpr(loc($MINUS, $p.ast.location()), AstPfxExpr.Oper.SUB, $p.ast);}
	| CARET p=prefix {$ast = new AstPfxExpr(loc($CARET, $p.ast.location()), AstPfxExpr.Oper.PTR, $p.ast);}
	| LESS type GREATER p=prefix {$ast = new AstCastExpr(loc($LESS, $p.ast.location()), $type.ast, $p.ast);}
	| postfix {$ast = $postfix.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your prefixes!");
	}

postfix returns [AstExpr ast]
	: p=postfix CARET {$ast = new AstSfxExpr(loc($p.ast.location(), $CARET), AstSfxExpr.Oper.PTR, $p.ast);}
	| p=postfix DOT IDENTIFIER {$ast = new AstCmpExpr(loc($p.ast.location(), $IDENTIFIER), $p.ast, $IDENTIFIER.text);}
	| p=postfix LEFTBRACKET expression RIGHTBRACKET {$ast = new AstArrExpr(loc($p.ast.location(), $RIGHTBRACKET), $p.ast, $expression.ast);}
	| sizeparen {$ast = $sizeparen.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your postfixes!");
	}

sizeparen returns [AstExpr ast]
	: LEFTPAREN expression RIGHTPAREN {
		$expression.ast.relocate(loc($LEFTPAREN, $RIGHTPAREN));
		$ast = $expression.ast;
	}
	| SIZEOF LEFTPAREN type RIGHTPAREN {
		$ast = new AstSizeofExpr(loc($SIZEOF, $RIGHTPAREN), $type.ast);
	}
	| terminal {$ast = $terminal.ast;}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check SIZEOF and parentheses!");
	}

terminal returns [AstExpr ast]
	: NONE {$ast = new AstAtomExpr(loc($NONE), AstAtomExpr.Type.VOID, $NONE.text);}
	| TRUE {$ast = new AstAtomExpr(loc($TRUE), AstAtomExpr.Type.BOOL, $TRUE.text);}
	| FALSE {$ast = new AstAtomExpr(loc($FALSE), AstAtomExpr.Type.BOOL, $FALSE.text);}
	| CHARCONST {$ast = new AstAtomExpr(loc($CHARCONST), AstAtomExpr.Type.CHAR, $CHARCONST.text);}
	| INTCONST {$ast = new AstAtomExpr(loc($INTCONST), AstAtomExpr.Type.INT, $INTCONST.text);} 
	| STRINGCONST {$ast = new AstAtomExpr(loc($STRINGCONST), AstAtomExpr.Type.STR, $STRINGCONST.text);}
	| NIL {$ast = new AstAtomExpr(loc($NIL), AstAtomExpr.Type.PTR, $NIL.text);}
	| IDENTIFIER {$ast = new AstNameExpr(loc($IDENTIFIER), $IDENTIFIER.text);}
	| IDENTIFIER LEFTPAREN RIGHTPAREN {
		$ast = new AstCallExpr(loc($IDENTIFIER, $RIGHTPAREN), $IDENTIFIER.text, null);
	}
    | IDENTIFIER LEFTPAREN expressions RIGHTPAREN {
		$ast = new AstCallExpr(loc($IDENTIFIER, $RIGHTPAREN), $IDENTIFIER.text, new AstNodes<AstExpr>($expressions.list));
	}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your constants!");
	}

expressions returns [List list]
	@init {$list = new ArrayList<AstExpr>();}
	: expression {$list.add($expression.ast);}
	| expression COMMA e=expressions {$list.add($expression.ast); $list.addAll($e.list);}
	;
	catch[Exception e] {
		Token token = getCurrentToken();
		Locatable location = new Location(token.getLine(), token.getCharPositionInLine());
		throw new Report.Error(location, "Syntax error at '" + token.getText() + "': Check your expressions!");
	}

