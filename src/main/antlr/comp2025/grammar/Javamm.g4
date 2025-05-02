grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

// Keywords (sempre antes do ID)
CLASS    : 'class' ;
EXTENDS  : 'extends' ;
PUBLIC   : 'public' ;
STATIC   : 'static' ;
VOID     : 'void' ;
INT_KW   : 'int' ;
BOOLEAN  : 'boolean' ;
DOUBLE   : 'double' ;
FLOAT    : 'float' ;
STRING   : 'String' ;
NEW      : 'new' ;
RETURN   : 'return' ;
IF       : 'if' ;
ELSE     : 'else' ;
WHILE    : 'while' ;
FOR      : 'for' ;
IMPORT   : 'import' ;

// Literais inteiros
INT_LITERAL
    : '0'
    | [1-9] [0-9]*
    ;

// Identificadores (agora não casam keywords nem dígitos)
ID
    : [a-zA-Z_$] [a-zA-Z0-9_$]*
    ;

// Comentários e espaços
MULTI_LINE_COMMENT  : '/*' .*? '*/'        -> skip ;
END_OF_LINE_COMMENT : '//' ~[\r\n]*        -> skip ;
WS                   : [ \t\r\n]+         -> skip ;

// ------------------ Parser Rules ------------------

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID ('.' name+=ID)* ';' #ImportStmt
    ;

classDecl
    : CLASS name=ID ( EXTENDS extendedClass=ID )? '{' ( varDecl )* ( methodDecl )* '}'
    ;

varDecl
    : type name=ID ';'
    ;

/** Aqui adicionámos VOID como um tipo possível */
type
    : VOID                                    #VoidType
    | value=( INT_KW | STRING | BOOLEAN | DOUBLE | FLOAT | ID )         #Var
    | value=( INT_KW | STRING | BOOLEAN | DOUBLE | FLOAT | ID ) '[' ']' #VarArray
    | value=INT_KW '...'                                                #VarArgs
    ;

methodDecl
    // 1ª alternativa: qualquer método (inclusive void foo(...))
    : PUBLIC? type name=ID '(' ( param ( ',' param )* )? ')'
      '{' ( varDecl )* ( stmt )* ( RETURN expr ';' )? '}'
    // 2ª alternativa: o static void main especial
    | PUBLIC? STATIC VOID 'main' '(' STRING '[' ']' name=ID ')' '{' ( varDecl )* ( stmt )* '}'
    ;

param
    : type name=ID #ParamExp
    ;

stmt
    : withElse
    | noElse
    ;

other
    : '{' ( stmt )* '}'                                       #BlockStmt
    | WHILE '(' expr ')' stmt                                 #WhileStmt
    | FOR '(' stmt expr ';' expr ')' stmt                     #ForStmt
    | expr ';'                                                #ExprStmt
    | name=ID '=' expr ';'                                    #AssignStmt
    | name=ID '[' expr ']' '=' expr ';'                       #AssignStmt
    ;

withElse
    : IF '(' expr ')' withElse ELSE withElse #WithElseStmt
    | other                                  #OtherStmt
    ;

noElse
    : IF '(' expr ')' stmt                                   #NoElseStmt
    | IF '(' expr ')' withElse ELSE noElse                   #NoElseStmt
    ;

expr
    : '(' expr ')'                                           #ParenthesizedExpr
    | '[' ( expr ( ',' expr )* )? ']'                        #ArrayLiteralExpr
    | value=INT_LITERAL                                      #IntegerLiteral
    | 'true'                                                 #BooleanTrue
    | 'false'                                                #BooleanFalse
    | value=ID                                               #VarRefExpr
    | 'this'                                                 #ThisExpr
    | op='!' expr                                            #UnaryExpr
    | NEW INT_KW '[' expr ']'                                #NewIntArrayExpr
    | NEW value=ID '(' ')'                                   #NewObjectExpr
    | value=ID op=('++' | '--')                              #PostfixExpr
    | expr '[' expr ']'                                      #ArrayAccessExpr
    | expr '.' 'length'                                      #ArrayLengthExpr
    | expr '.' method=ID '(' ( expr ( ',' expr )* )? ')'     #MethodCallExpr
    | expr op=('*' | '/') expr                               #BinaryExpr
    | expr op=('+' | '-') expr                               #BinaryExpr
    | expr op=('<' | '>') expr                               #BinaryExpr
    | expr op=('<=' | '>=' | '==' | '!=') expr               #BinaryExpr
    | expr op='&&' expr                                      #BinaryExpr
    | expr op='||' expr                                      #BinaryExpr
    | expr op=('+=' | '-=' | '*=' | '/=') expr               #BinaryExpr
    ;
