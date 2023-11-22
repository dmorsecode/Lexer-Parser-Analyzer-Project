package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        throw new UnsupportedOperationException(); //TODO
        //return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        throw new UnsupportedOperationException(); //TODO
        //return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        throw new UnsupportedOperationException(); //TODO
        //return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(),
                " ",
                ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        throw new UnsupportedOperationException(); //TODO
        //return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        throw new UnsupportedOperationException(); //TODO
        //return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (", ast.getCondition(), ") {");
        if (!ast.getStatements().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getType() == Environment.Type.INTEGER) {
            print((BigInteger) ast.getLiteral());
        } else if (ast.getType() == Environment.Type.DECIMAL) {
            print((BigDecimal) ast.getLiteral());
        } else if (ast.getType() == Environment.Type.CHARACTER) {
            print("'", ast.getLiteral(), "'");
        } else if (ast.getType() == Environment.Type.STRING) {
            print("\"", ast.getLiteral(), "\"");
        } else print(ast.getLiteral());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        print(ast.getLeft(), " ");
        if (ast.getOperator().equals("AND")) print("&&");
        else if (ast.getOperator().equals("OR")) print("||");
        else print(ast.getOperator());
        print(" ", ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) print(ast.getReceiver().get(), ".");
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if (ast.getReceiver().isPresent()) print(ast.getReceiver().get(), ".");
        print(ast.getFunction().getJvmName(), "(");
        if (!ast.getArguments().isEmpty()) {
            for (int i = 0; i < ast.getArguments().size(); i++) {
                print(ast.getArguments().get(i));
                if (i != ast.getArguments().size() - 1) print(", ");
            }
        }
        print(")");
        return null;
    }

}
