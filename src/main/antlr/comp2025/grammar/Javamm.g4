 grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

INT : '0' | [1-9] [0-9]*;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]*;


MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
END_OF_LINE_COMMENT : '//' .*? '\n' -> skip ;
WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' ID ('.' ID)* ';' #ImportStmt
    ;

classDecl
    : 'class' name=ID ( 'extends' ID )? '{' ( varDecl )* ( methodDecl )* '}'
    ;

varDecl
    : type ID ';'
    ;

methodDecl
    : ('public')? type name=ID '(' ( type ID ( ',' type ID )* )? ')' '{' ( varDecl)* ( stmt )* 'return' expr ';' '}'
    | ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' ID ')' '{' ( varDecl )* ( stmt )* '}'
    ;

type
    : value=( 'int' | 'String' | 'boolean' | 'double' | 'float' | ID ) #Var
    | value=( 'int' | 'String' | 'boolean' | 'double' | 'float' | ID ) '[' ']' #VarArray
    | value='int' '...'  #VarArgs
    ;

stmt
    : withElse
    | noElse
    ;

otherStmt
    : '{' ( stmt )* '}'
    | 'while' '(' expr ')' stmt
    | 'for' '(' stmt expr ';' expr ')' stmt
    | expr ';'
    | ID '=' expr ';'
    | ID '[' expr ']' '=' expr ';'
    ;

withElse
    : 'if' '(' expr ')' withElse 'else' withElse
    | otherStmt
    ;

noElse
    : 'if' '(' expr ')' stmt
    | 'if' '(' expr ')' withElse 'else' noElse
    ;

expr
    : '(' expr ')' #ParenthesizedExpr
    | '[' ( expr ( ',' expr )* )? ']' #ArrayLiteralExpr
    | value=INT #IntegerLiteral
    | value='true' #BooleanTrue
    | value='false' #BooleanFalse
    | value=ID #IdentifierExpr
    | 'this' #ThisExpr
    | op='!' expr #UnaryExpr
    | 'new' 'int' '[' expr ']' #NewIntArrayExpr
    | 'new' value=ID '(' ')' #NewObjectExpr
    | value=ID op=('++' | '--') #PostfixExpr
    | expr '[' expr ']' #ArrayAccessExpr
    | expr '.' 'length' #ArrayLengthExpr
    | expr '.' method=ID '(' ( expr ( ',' expr )* )? ')' #MethodCallExpr
    | expr op=('*' | '/') expr #MultiplicativeExpr
    | expr op=('+' | '-') expr #AdditiveExpr
    | expr op=('<' | '>') expr #RelationalExpr
    | expr op=('<=' | '>=' | '==' | '!=') expr #EqualityExpr
    | expr op='&&' expr #LogicalAndExpr
    | expr op='||' expr #LogicalOrExpr
    | expr op=('+=' | '-=' | '*=' | '/=') expr #AssignmentExpr
    ;



