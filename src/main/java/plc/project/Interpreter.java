package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (Ast.Field field : ast.getFields()) visit(field);
        for (Ast.Method method : ast.getMethods()) visit(method);
        List<Environment.PlcObject> args = new ArrayList<Environment.PlcObject>();
        return scope.lookupFunction("main", 0).invoke(args);
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if (ast.getValue().isPresent())
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        else
            scope.defineVariable(ast.getName(), Environment.NIL);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            try {
                scope = new Scope(scope);
                for (String param : ast.getParameters()) {
                    for (Environment.PlcObject arg : args) {
                        scope.defineVariable(param, arg);
                    }
                }
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } catch (Return ret) {
                return ret.value;
            } finally {
                scope = scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        } else {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        Ast.Expr receiver = ast.getReceiver();
        if (receiver.getClass() == Ast.Expr.Access.class && ((Ast.Expr.Access) receiver).getReceiver().isPresent()) {
            Environment.PlcObject r = visit(((Ast.Expr.Access) receiver).getReceiver().get());
            r.setField(((Ast.Expr.Access) receiver).getName(), visit(ast.getValue()));
        } else if (receiver.getClass() == Ast.Expr.Access.class && !((Ast.Expr.Access) receiver).getReceiver().isPresent()) {
            Environment.Variable var = scope.lookupVariable(((Ast.Expr.Access) receiver).getName());
            var.setValue(visit(ast.getValue()));
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        if (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for (Ast.Stmt stmt : ast.getThenStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        } else {
            try {
                scope = new Scope(scope);
                for (Ast.Stmt stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        Iterable<Environment.PlcObject> iterator = requireType(Iterable.class, visit(ast.getValue()));
        for (Environment.PlcObject obj : iterator) {
            try {
                scope = new Scope(scope);
                scope.defineVariable(ast.getName(), obj);
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        }
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        Environment.PlcObject lhs = visit(ast.getLeft());
        //Environment.PlcObject rhs =
        String op = ast.getOperator();
        if (op.equals("AND")) {
            if (requireType(Boolean.class, lhs) == requireType(Boolean.class, visit(ast.getRight())))
                return Environment.create(true);
            else
                return Environment.create(Boolean.FALSE);
        } else if (op.equals("OR")) {
            if (requireType(Boolean.class, lhs) && (Boolean) lhs.getValue()) return Environment.create(true);
            if (requireType(Boolean.class, visit(ast.getRight())) && (Boolean) visit(ast.getRight()).getValue())
                return Environment.create(true);
            return Environment.create(false);
        } else if (op.equals("<")) {
            if (lhs.getValue().getClass() == visit(ast.getRight()).getValue().getClass()) {
                return Environment.create(((Comparable) lhs.getValue()).compareTo(visit(ast.getRight()).getValue()) < 0);
            }
        } else if (op.equals("<=")) {
            if (lhs.getValue().getClass() == visit(ast.getRight()).getValue().getClass()) {
                return Environment.create(((Comparable) lhs.getValue()).compareTo(visit(ast.getRight()).getValue()) <= 0);
            }
        } else if (op.equals(">")) {
            if (lhs.getValue().getClass() == visit(ast.getRight()).getValue().getClass()) {
                return Environment.create(((Comparable) lhs.getValue()).compareTo(visit(ast.getRight()).getValue()) > 0);
            }
        } else if (op.equals(">=")) {
            if (lhs.getValue().getClass() == visit(ast.getRight()).getValue().getClass()) {
                return Environment.create(((Comparable) lhs.getValue()).compareTo(visit(ast.getRight()).getValue()) >= 0);
            }
        } else if (op.equals("==")) {
            return Environment.create(lhs.getValue().equals(visit(ast.getRight()).getValue()));
        } else if (op.equals("!=")) {
            return Environment.create(!lhs.getValue().equals(visit(ast.getRight()).getValue()));
        } else if (op.equals("+")) {
            Environment.PlcObject rhs = visit(ast.getRight());
            if (lhs.getValue().getClass() == BigInteger.class && rhs.getValue().getClass() == BigInteger.class) {
                return Environment.create(requireType(BigInteger.class, lhs).add(requireType(BigInteger.class, rhs)));
            }
            if (lhs.getValue().getClass() == BigDecimal.class && rhs.getValue().getClass() == BigDecimal.class) {
                return Environment.create(requireType(BigDecimal.class, lhs).add(requireType(BigDecimal.class, rhs)));
            }
            if (lhs.getValue().getClass() == String.class && rhs.getValue().getClass() == String.class) {
                return Environment.create(requireType(String.class, lhs) + (requireType(String.class, rhs)));
            }
            throw new RuntimeException();
        } else if (op.equals("-")) {
            Environment.PlcObject rhs = visit(ast.getRight());
            if (lhs.getValue().getClass() == BigInteger.class && rhs.getValue().getClass() == BigInteger.class) {
                return Environment.create(requireType(BigInteger.class, lhs).subtract(requireType(BigInteger.class, rhs)));
            }
            if (lhs.getValue().getClass() == BigDecimal.class && rhs.getValue().getClass() == BigDecimal.class) {
                return Environment.create(requireType(BigDecimal.class, lhs).subtract(requireType(BigDecimal.class, rhs)));
            }
            throw new RuntimeException();
        } else if (op.equals("*")) {
            Environment.PlcObject rhs = visit(ast.getRight());
            if (lhs.getValue().getClass() == BigInteger.class && rhs.getValue().getClass() == BigInteger.class) {
                return Environment.create(requireType(BigInteger.class, lhs).multiply(requireType(BigInteger.class, rhs)));
            }
            if (lhs.getValue().getClass() == BigDecimal.class && rhs.getValue().getClass() == BigDecimal.class) {
                return Environment.create(requireType(BigDecimal.class, lhs).multiply(requireType(BigDecimal.class, rhs)));
            }
            throw new RuntimeException();
        } else if (op.equals("/")) {
            Environment.PlcObject rhs = visit(ast.getRight());
            if (rhs.getValue().equals(BigInteger.ZERO) || rhs.getValue().equals(BigDecimal.ZERO))
                throw new RuntimeException();
            if (lhs.getValue().getClass() == BigInteger.class && rhs.getValue().getClass() == BigInteger.class) {
                return Environment.create(requireType(BigInteger.class, lhs).divide(requireType(BigInteger.class, rhs)));
            }
            if (lhs.getValue().getClass() == BigDecimal.class && rhs.getValue().getClass() == BigDecimal.class) {
                return Environment.create(requireType(BigDecimal.class, lhs).divide(requireType(BigDecimal.class, rhs), RoundingMode.HALF_EVEN));
            }
            throw new RuntimeException();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            return visit(ast.getReceiver().get()).getField(ast.getName()).getValue();
        }
        return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        try {
            scope = new Scope(scope);
            List<Environment.PlcObject> args = new ArrayList<>();
            for (Ast.Expr arg : ast.getArguments()) {
                args.add(visit(arg));
            }
            if (ast.getReceiver().isPresent()) {
                Environment.PlcObject receiver = visit(ast.getReceiver().get());
                return receiver.callMethod(ast.getName(), args);
            } else {
                return scope.lookupFunction(ast.getName(), args.size()).invoke(args);
            }
        } finally {
            scope = scope.getParent();
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
