package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        if (scope.lookupFunction("main", 0) == null || scope.lookupFunction("main", 0).getReturnType() != Environment.Type.INTEGER) {
            throw new RuntimeException("Invalid main function.");
        }
        for (Ast.Field field : ast.getFields()) visit(field);
        for (Ast.Method method : ast.getMethods()) visit(method);
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
            scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), Environment.NIL);
        } else {
            scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), Environment.NIL);
        }
        ast.setVariable(scope.lookupVariable(ast.getName()));
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        List<Environment.Type> types = new ArrayList<Environment.Type>();
        for (String type : ast.getParameterTypeNames()) types.add(Environment.getType(type));
        Environment.Type returnType = Environment.Type.NIL;
        if (ast.getReturnTypeName().isPresent()) returnType = Environment.getType(ast.getReturnTypeName().get());
        ast.setFunction(scope.defineFunction(ast.getName(), ast.getName(), types, returnType, args -> Environment.NIL));
        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), types.get(i), Environment.NIL);
            }
            for (Ast.Stmt stmt : ast.getStatements()) visit(stmt);
        } finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        if (!(ast.getExpression() instanceof Ast.Expr.Function))
            throw new RuntimeException("Not an instance of Ast.Expr.Function.");
        visit(ast.getExpression());
        return null;
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
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) throw new RuntimeException("Expected Access expression.");
        visit(ast.getReceiver());
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        if (ast.getThenStatements().isEmpty()) throw new RuntimeException("No Then statements.");
        for (Ast.Stmt thens : ast.getThenStatements()) {
            try {
                scope = new Scope(scope);
                visit(thens);
            } finally {
                scope = scope.getParent();
            }
        }
        for (Ast.Stmt elses : ast.getElseStatements()) {
            try {
                scope = new Scope(scope);
                visit(elses);
            } finally {
                scope = scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        visit(ast.getValue());
        requireAssignable(Environment.Type.INTEGER_ITERABLE, ast.getValue().getType());
        if (ast.getStatements().isEmpty()) throw new RuntimeException("Empty For-Loop.");
        try {
            scope = new Scope(scope);
            scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);
            for (Ast.Stmt stmt : ast.getStatements()) visit(stmt);
        } finally {
            scope = scope.getParent();
        }
        return null;
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
        requireAssignable(scope.lookupVariable("returnType").getType(), ast.getValue().getType());
        return null;
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
        if (!(ast.getExpression() instanceof Ast.Expr.Binary))
            throw new RuntimeException("Expected binary expression.");
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        Ast.Expr lhs = ast.getLeft();
        Ast.Expr rhs = ast.getRight();
        String op = ast.getOperator();
        visit(lhs);
        visit(rhs);
        if (op.equals("AND") || op.equals("OR")) {
            requireAssignable(Environment.Type.BOOLEAN, lhs.getType());
            requireAssignable(Environment.Type.BOOLEAN, rhs.getType());
            ast.setType(Environment.Type.BOOLEAN);
        } else if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=") || op.equals("==") || op.equals("!=")) {
            requireAssignable(Environment.Type.COMPARABLE, lhs.getType());
            requireAssignable(Environment.Type.COMPARABLE, rhs.getType());
            ast.setType(Environment.Type.BOOLEAN);
        } else if (op.equals("+")) {
            if (lhs.getType() == Environment.Type.STRING || rhs.getType() == Environment.Type.STRING)
                ast.setType(Environment.Type.STRING);
            else if (lhs.getType() == Environment.Type.INTEGER || lhs.getType() == Environment.Type.DECIMAL) {
                if (lhs.getType() != rhs.getType()) throw new RuntimeException("Non-matching binary expressions.");
                ast.setType(lhs.getType());
            } else throw new RuntimeException("Left-side binary expression must be string, integer, or decimal.");
        } else if (op.equals("-") || op.equals("*") || op.equals("/")) {
            if (lhs.getType() == Environment.Type.INTEGER || lhs.getType() == Environment.Type.DECIMAL) {
                if (lhs.getType() != rhs.getType()) throw new RuntimeException("Non-matching binary expressions.");
                ast.setType(lhs.getType());
            } else throw new RuntimeException("Left-side binary expression must be  an integer or decimal.");
        } else throw new RuntimeException("Invalid binary expression operator.");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            Ast.Expr.Access expr = (Ast.Expr.Access) ast.getReceiver().get();
            expr.setVariable(scope.lookupVariable(expr.getName()));
            try {
                scope = scope.lookupVariable(expr.getName()).getType().getScope();
                ast.setVariable(scope.lookupVariable(ast.getName()));
            } finally {
                scope = scope.getParent();
            }
        } else {
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        List<Ast.Expr> args = ast.getArguments();
        if (ast.getReceiver().isPresent()) {
            Ast.Expr expr = ast.getReceiver().get();
            visit(expr);
            List<Environment.Type> types = expr.getType().getMethod(ast.getName(), ast.getArguments().size()).getParameterTypes();
            for (int i = 0; i < args.size(); i++) {
                visit(args.get(i));
                requireAssignable(types.get(i + 1), args.get(i).getType());
            }
            ast.setFunction(expr.getType().getMethod(ast.getName(), ast.getArguments().size()));
        } else {
            List<Environment.Type> types = scope.lookupFunction(ast.getName(), ast.getArguments().size()).getParameterTypes();
            for (int i = 0; i < args.size(); i++) {
                visit(args.get(i));
                requireAssignable(types.get(i), args.get(i).getType());
            }
            ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target != type && target != Environment.Type.ANY && target != Environment.Type.COMPARABLE) {
            throw new RuntimeException("Variable does not match required type.");
        }
    }

}
