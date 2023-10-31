package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Field ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Method ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        // 'LET' identifier (':' identifier)? ('=' expression)? ';

        if (!ast.getTypeName().isPresent() && !ast.getValue().isPresent())
            throw new RuntimeException("Declaration must have type or value to infer type.");

        Environment.Type type = null;

        if (ast.getTypeName().isPresent()) type = Environment.getType(ast.getTypeName().get());

        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());

            if (type == null) {
                type = ast.getValue().get().getType();
            }

            requireAssignable(type, ast.getValue().get().getType());
        }

        Environment.Variable var = scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL);
        ast.setVariable(var);

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        } finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() instanceof String) ast.setType(Environment.Type.STRING);
        else if (ast.getLiteral() instanceof Character) ast.setType(Environment.Type.CHARACTER);
        else if (ast.getLiteral() instanceof Boolean) ast.setType(Environment.Type.BOOLEAN);
        else if (ast.getLiteral() instanceof BigInteger) {
            BigInteger value = (BigInteger) ast.getLiteral();
            if (value.longValue() > Integer.MAX_VALUE || value.longValue() < Integer.MIN_VALUE)
                throw new RuntimeException("Integer outside of range.");
            ast.setType(Environment.Type.INTEGER);
        } else if (ast.getLiteral() instanceof BigDecimal) {
            BigDecimal value = (BigDecimal) ast.getLiteral();
            if (value.doubleValue() > Double.MAX_VALUE || value.doubleValue() < Double.MIN_VALUE)
                throw new RuntimeException("Decimal outside of range.");
            ast.setType(Environment.Type.DECIMAL);
        } else if (ast.getLiteral() == Environment.NIL) ast.setType(Environment.Type.NIL);
        else throw new RuntimeException();
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target != type && target != Environment.Type.ANY && target != Environment.Type.COMPARABLE) {
            throw new RuntimeException("Variable does not match required type.");
        }
    }

}
