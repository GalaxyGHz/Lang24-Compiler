lexer grammar Lang24Lexer;

@header {
	package lang24.phase.lexan;
	import lang24.common.report.*;
	import lang24.data.token.*;
}

@members {
    @Override
	public LocLogToken nextToken() {
		return (LocLogToken) super.nextToken();
	}
}

// Fragments used for literals
fragment HEXCHAR : [0-9A-F] ;
fragment SINGLEQUOTE : '\'' ;
fragment DOUBLEQUOTE : '"' ;
fragment ASCIINOSINGLEQUOTEANDBACKSLASH : [\u0020-\u0026\u0028-\u005B\u005D-\u007E] ;
fragment ASCIINODOUBLEQUOTEANDBACKSLASH : [\u0020-\u0021\u0023-\u005B\u005D-\u007E] ;

// Literals
INTCONST : [0-9]+ ;
CHARCONST : SINGLEQUOTE (ASCIINOSINGLEQUOTEANDBACKSLASH | '\\\\' | '\\\'' | '\\n' | '\\' HEXCHAR HEXCHAR) SINGLEQUOTE ;
STRINGCONST : DOUBLEQUOTE (ASCIINODOUBLEQUOTEANDBACKSLASH | '\\\\' | '\\"' | '\\n' | '\\' HEXCHAR HEXCHAR)* DOUBLEQUOTE ;

// Symbols
LEFTPAREN : '(' ;
RIGHTPAREN : ')' ;
LEFTBRACE : '{' ;
RIGHTBRACE : '}' ;
LEFTBRACKET : '[' ;
RIGHTBRACKET : ']' ;
DOT : '.' ;
COMMA : ',' ;
COLON : ':' ;
SEMICOLON : ';' ;
EQUAL : '==' ;
NOTEQUAL : '!=' ;
LESS : '<' ;
GREATER : '>' ;
LESSEQUAL : '<=' ;
GREATEREQUAL : '>=' ;
STAR : '*' ;
DIV : '/' ;
MOD : '%' ;
PLUS : '+' ;
MINUS : '-' ;
CARET : '^' ;
ASSIGN : '=' ;

// Keywords
AND : 'and' ;
BOOL : 'bool' ;
CHAR : 'char' ;
ELSE : 'else' ;
IF : 'if' ;
INT : 'int' ;
NIL : 'nil' ;
NONE : 'none' ;
NOT : 'not' ;
OR : 'or' ;
SIZEOF : 'sizeof' ;
THEN : 'then' ;
RETURN : 'return' ;
VOID : 'void' ;
WHILE : 'while' ;
TRUE : 'true' ;
FALSE : 'false' ;

// Identifiers
IDENTIFIER : [A-Za-z_][A-Za-z0-9_]* ;

// Comments
COMMENT : '#' ~[\r\n]* -> skip ;

// Tab
TAB : '\t' {
    setCharPositionInLine(getCharPositionInLine() + (8 - getCharPositionInLine() % 8));
} -> skip ;

// Whitespace
WS : [ \r\n]+ -> skip ;

// Error
ERROR : . {
    if (true) {
        final Locatable location = new Location(getLine(), getCharPositionInLine());
		String text = getText();
		if (text.equals("'")) {
			throw new Report.Error(location, "Error: There was a problem while lexing a char! Check your single quotes.");
		} else if (text.equals("\"")) {
			throw new Report.Error(location, "Error: There was a problem while lexing a string! Check your double quotes.");
		} else {
			throw new Report.Error(location, "Error: There was a problem while lexing. Encountered an illegal token.");
		}
    }
} ;
