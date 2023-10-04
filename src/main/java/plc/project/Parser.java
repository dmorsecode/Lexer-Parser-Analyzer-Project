package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 * <p>
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 * <p>
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    // source ::= field* method*
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();
        try {
            while (tokens.has(0)) {
                if (match("LET")) {
                    fields.add(parseField());
                } else if (match("DEF")) {
                    methods.add(parseMethod());
                }
            }
            return new Ast.Source(fields, methods);
        } catch (ParseException ex) {
            throw new ParseException("Invalid source code.", tokens.get(0).getIndex());
        }
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        try {
            Ast.Stmt.Declaration dec = parseDeclarationStatement();
            return new Ast.Field(dec.getName(), dec.getValue());
        } catch (ParseException ex) {
            throw new ParseException("Invalid declaration.", tokens.get(0).getIndex());
        }
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        try {
            if (match(Token.Type.IDENTIFIER)) {
                String name = tokens.get(-1).getLiteral();
                if (match("(")) {
                    List<String> params = new ArrayList<>();
                    while (match(Token.Type.IDENTIFIER)) {
                        params.add(tokens.get(-1).getLiteral());
                        if (!match(",") && !match(")"))
                            throw new ParseException("Missing comma between parameters.", tokens.get(0).getIndex());
                    }
                    if (!match(")")) throw new ParseException("Missing closing parenthesis.", tokens.get(0).getIndex());
                    if (!match("DO")) throw new ParseException("Expected DO statement.", tokens.get(0).getIndex());
                    List<Ast.Stmt> statements = new ArrayList<>();
                    while (!match("END")) {
                        statements.add(parseStatement());
                    }
                    return new Ast.Method(name, params, statements);
                } else throw new ParseException("Expected parenthesis.", tokens.get(0).getIndex());
            } else throw new ParseException("Expected method identifier.", tokens.get(0).getIndex());
        } catch (ParseException ex) {
            throw new ParseException("Invalid method.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        try {
            if (match("LET")) {
                return parseDeclarationStatement();
            } else if (match("IF")) {
                return parseIfStatement();
            } else if (match("FOR")) {
                return parseForStatement();
            } else if (match("WHILE")) {
                return parseWhileStatement();
            } else if (match("RETURN")) {
                return parseReturnStatement();
            } else {
                Ast.Expr expr = parseExpression();
                if (!match("=")) {
                    if (!match(";")) throw new ParseException("Missing semicolon.", tokens.get(-1).getIndex());
                    return new Ast.Stmt.Expression(expr);
                }
                Ast.Expr rhs = parseExpression();
                if (!match(";")) throw new ParseException("Missing semicolon.", tokens.get(-1).getIndex());
                return new Ast.Stmt.Assignment(expr, rhs);
            }
        } catch (ParseException ex) {
            throw new ParseException("Error parsing statement.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        try {
            if (match(Token.Type.IDENTIFIER)) {
                String name = tokens.get(-1).getLiteral();
                if (match("=")) {
                    Ast.Expr rhs = parseExpression();
                    if (match(";")) {
                        return new Ast.Stmt.Declaration(name, Optional.of(rhs));
                    } else throw new ParseException("Missing semicolon.", tokens.get(0).getIndex());
                } else if (match(";")) {
                    return new Ast.Stmt.Declaration(name, Optional.empty());
                } else throw new ParseException("Missing semicolon.", tokens.get(0).getIndex());
            } else throw new ParseException("Invalid identifier.", tokens.get(0).getIndex());
        } catch (ParseException ex) {
            throw new ParseException("Invalid declaration statement.", tokens.get(0).getIndex());
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        try {
            Ast.Expr expr = parseExpression();
            if (match("DO")) {
                List<Ast.Stmt> dos = new ArrayList<>();
                List<Ast.Stmt> elses = new ArrayList<>();
                while (!match("ELSE") && !match("END")) {
                    dos.add(parseStatement());
                }
                if (tokens.get(-1).getLiteral().equals("ELSE")) {
                    while (!match("END")) elses.add(parseStatement());
                }
                return new Ast.Stmt.If(expr, dos, elses);
            } else throw new ParseException("Missing DO.", tokens.get(0).getIndex());
        } catch (ParseException ex) {
            throw new ParseException("Invalid if statement.", tokens.get(0).getIndex());
        }
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        try {
            return parseLogicalExpression();
        } catch (ParseException ex) {
            throw new ParseException("Error parsing expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        try {
            Ast.Expr expr = parseEqualityExpression();
            while (peek("AND") || peek("OR")) {
                String op = tokens.get(0).getLiteral();
                match(Token.Type.IDENTIFIER);
                Ast.Expr rhs = parseEqualityExpression();
                expr = new Ast.Expr.Binary(op, expr, rhs);
            }
            return expr;
        } catch (ParseException ex) {
            throw new ParseException("Error parsing logical expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        try {
            Ast.Expr expr = parseAdditiveExpression();
            while (peek("<") || peek("<=") || peek(">") || peek(">=") || peek("==") || peek("!=")) {
                String op = tokens.get(0).getLiteral();
                match(Token.Type.OPERATOR);
                Ast.Expr rhs = parseAdditiveExpression();
                expr = new Ast.Expr.Binary(op, expr, rhs);
            }
            return expr;
        } catch (ParseException ex) {
            throw new ParseException("Error parsing equality expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        try {
            Ast.Expr expr = parseMultiplicativeExpression();
            while (peek("+") || peek("-")) {
                String op = tokens.get(0).getLiteral();
                match(Token.Type.OPERATOR);
                Ast.Expr rhs = parseMultiplicativeExpression();
                expr = new Ast.Expr.Binary(op, expr, rhs);
            }
            return expr;
        } catch (ParseException ex) {
            throw new ParseException("Error parsing additive expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        try {
            Ast.Expr expr = parseSecondaryExpression();
            while (peek("/") || peek("*")) {
                String op = tokens.get(0).getLiteral();
                match(Token.Type.OPERATOR);
                Ast.Expr rhs = parseSecondaryExpression();
                expr = new Ast.Expr.Binary(op, expr, rhs);
            }
            return expr;
        } catch (ParseException ex) {
            throw new ParseException("Error parsing multiplicative expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        try {
            Ast.Expr expr = parsePrimaryExpression();
            while (peek(".")) {
                match(".");
                if (!match(Token.Type.IDENTIFIER))
                    throw new ParseException("Invalid identifier following secondary expression.", tokens.get(0).getIndex());
                String name = tokens.get(-1).getLiteral();
                if (!match("(")) {
                    expr = new Ast.Expr.Access(Optional.of(expr), name);
                } else {
                    List<Ast.Expr> fncArgs = new ArrayList<>();
                    if (!match(")")) {
                        fncArgs.add(parseExpression());
                        while (peek(",")) {
                            match(",");
                            fncArgs.add(parseExpression());
                        }
                        if (!match(")")) {
                            throw new ParseException("Invalid function call.", tokens.get(0).getIndex());
                        }
                    }
                    expr = new Ast.Expr.Function(Optional.of(expr), name, fncArgs);
                }
            }
            return expr;
        } catch (ParseException ex) {
            throw new ParseException("Error parsing secondary expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        try {
            if (match("NIL")) {
                return new Ast.Expr.Literal(null);
            } else if (match("TRUE")) {
                return new Ast.Expr.Literal(true);
            } else if (match("FALSE")) {
                return new Ast.Expr.Literal(false);
            } else if (match(Token.Type.INTEGER)) {
                return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
            } else if (match(Token.Type.DECIMAL)) {
                return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
            } else if (match(Token.Type.CHARACTER)) {
                String temp = tokens.get(-1).getLiteral();
                temp = temp.replace("\\b", "\b")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\'", "\'")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                return new Ast.Expr.Literal(temp.charAt(1));
            } else if (match(Token.Type.STRING)) {
                String string = tokens.get(-1).getLiteral();
                string = string.replace("\\b", "\b")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\'", "\'")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                string = string.substring(1, string.length() - 1); // Remove quotes.
                return new Ast.Expr.Literal(string);
            } else if (match(Token.Type.IDENTIFIER)) {
                String name = tokens.get(-1).getLiteral();
                if (!match("(")) {
                    return new Ast.Expr.Access(Optional.empty(), name);
                } else {
                    List<Ast.Expr> fncArgs = new ArrayList<Ast.Expr>();
                    while (!match(")")) {
                        fncArgs.add(parseExpression());
                        if (match(",")) {
                            if (match(")"))
                                throw new ParseException("Missing argument in function call.", tokens.get(0).getIndex());
                        }
                    }
                    return new Ast.Expr.Function(Optional.empty(), name, fncArgs);
                }
            } else if (match("(")) {
                Ast.Expr expr = parseExpression();
                if (!match(")")) {
                    throw new ParseException("Expected closing parenthesis.", tokens.get(0).getIndex());
                }
                return new Ast.Expr.Group(expr);
            } else {
                throw new ParseException("Invalid primary expression.", tokens.get(-1).getIndex());
            }
        } catch (ParseException ex) {
            throw new ParseException("Invalid primary expression.", tokens.get(-1).getIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     * <p>
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
