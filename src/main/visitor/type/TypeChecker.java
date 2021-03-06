package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.Identifier;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.types.FptrType;
import main.ast.types.NoType;
import main.ast.types.StructType;
import main.ast.types.Type;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.ast.types.primitives.VoidType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemAlreadyExistsException;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.visitor.Visitor;

import java.util.Stack;

public class TypeChecker extends Visitor<Void> {

    private boolean inMain;
    private boolean inSetter;
    private boolean inSetterGetter;
    ExpressionTypeChecker expressionTypeChecker;
    private final Stack<Type> retType = new Stack<>();

    public TypeChecker() {
        this.expressionTypeChecker = new ExpressionTypeChecker();
    }

    @Override
    public Void visit(Program program) {
        inMain = false;
        for (StructDeclaration structDeclaration : program.getStructs())
            structDeclaration.accept(this);
        for (FunctionDeclaration functionDeclaration : program.getFunctions())
            functionDeclaration.accept(this);
        inMain = true;
        retType.push(new NoType());
        program.getMain().accept(this);
        return null;
    }

    @Override
    public Void visit(FunctionDeclaration functionDec) {
        SymbolTable.push(new SymbolTable());
        retType.push(functionDec.getReturnType());
        StructType type = null;
        if (retType.peek() instanceof StructType) {
            try {
                type = (StructType) retType.peek();
                SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + type.getStructName().getName());
            } catch (ItemNotFoundException exception) {
                functionDec.addError(new StructNotDeclared(functionDec.getLine(), type.getStructName().getName()));
            }
        }
        for (VariableDeclaration arg : functionDec.getArgs()) arg.accept(this);
        functionDec.getBody().accept(this);
        if (!haveReturn(functionDec.getBody()))
            functionDec.addError(new MissingReturnStatement
                    (functionDec.getLine(), functionDec.getFunctionName().getName()));
        retType.pop();
        SymbolTable.pop();
        return null;
    }

    private boolean haveReturn(Statement statement) {
        if (statement == null) return false;
        if (retType.peek() instanceof VoidType) return true;
        if (statement instanceof ReturnStmt) return true;
        if (!(statement instanceof BlockStmt) && !(statement instanceof LoopStmt)
                && !(statement instanceof ConditionalStmt) && !(statement instanceof ReturnStmt))
            return false;
        if (statement instanceof LoopStmt)
            if (haveReturn(((LoopStmt) statement).getBody())) return true;
        if (statement instanceof ConditionalStmt) {
            boolean thenReturn = false, elseReturn = false;
            thenReturn = haveReturn(((ConditionalStmt) statement).getThenBody());
            elseReturn = haveReturn(((ConditionalStmt) statement).getElseBody());
            return thenReturn && elseReturn;
        }
        boolean pathReturn = true;
        if (statement instanceof BlockStmt) {
            for (Statement stmt : ((BlockStmt) statement).getStatements()) {
                if (stmt instanceof ReturnStmt) return true;
                if (stmt instanceof ConditionalStmt) {
                    boolean thenReturn = false, elseReturn = false;
                    thenReturn = haveReturn(((ConditionalStmt) stmt).getThenBody());
                    elseReturn = haveReturn(((ConditionalStmt) stmt).getElseBody());
                    pathReturn = pathReturn && (thenReturn && elseReturn);
                }
            }
        }

        return pathReturn;
    }

    @Override
    public Void visit(MainDeclaration mainDec) {
        SymbolTable.push(new SymbolTable(SymbolTable.root));
        mainDec.getBody().accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDec) {
        if (inSetterGetter) variableDec.addError(new CannotUseDefineVar(variableDec.getLine()));
        VariableSymbolTableItem variableSymbolTableItem = new VariableSymbolTableItem(variableDec.getVarName());
        variableSymbolTableItem.setType(variableDec.getVarType());
        if (variableDec.getVarType() instanceof StructType) {
            try {
                StructType structType = (StructType) variableDec.getVarType();
                Identifier structTypeName = structType.getStructName();
                SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + structTypeName.getName());
            } catch (ItemNotFoundException exception) {
                StructType structType = (StructType) variableDec.getVarType();
                variableSymbolTableItem.setType(new NoType());
                variableDec.addError(
                        new StructNotDeclared(variableDec.getLine(), structType.getStructName().getName()));
            }
        }
        if (variableDec.getVarType() instanceof FptrType)
            for (Type type : ((FptrType) variableDec.getVarType()).getArgsType())
                if (type instanceof StructType) try {
                    StructType structType = (StructType) type;
                    Identifier structTypeName = structType.getStructName();
                    SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + structTypeName.getName());
                } catch (ItemNotFoundException exception) {
                    StructType structType = (StructType) type;
                    variableSymbolTableItem.setType(new NoType());
                    variableDec.addError(new StructNotDeclared(variableDec.getLine(), structType.getStructName().getName()));
                }
        try {
            SymbolTable.top.put(variableSymbolTableItem);
        } catch (ItemAlreadyExistsException ignored) {
            try {
                VariableSymbolTableItem symbolTableItem =
                        (VariableSymbolTableItem) SymbolTable.top.getItem(variableSymbolTableItem.getKey());
                symbolTableItem.setType(variableSymbolTableItem.getType());
            } catch (ItemNotFoundException ignored1) {
            }
        }
        if (variableDec.getDefaultValue() != null) variableDec.getDefaultValue().accept(expressionTypeChecker);
        return null;
    }

    @Override
    public Void visit(StructDeclaration structDec) {
        try {
            StructSymbolTableItem symbolTableItem = (StructSymbolTableItem)
                    SymbolTable.root.getItem(
                            StructSymbolTableItem.START_KEY + structDec.getStructName().getName());
            SymbolTable.push(symbolTableItem.getStructSymbolTable());
            structDec.getBody().accept(this);
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public Void visit(SetGetVarDeclaration setGetVarDec) {
        try {
            VariableSymbolTableItem variableSymbolTableItem = new VariableSymbolTableItem(setGetVarDec.getVarName());
            variableSymbolTableItem.setType(setGetVarDec.getVarType());
            try {
                SymbolTable.top.put(variableSymbolTableItem);
            } catch (ItemAlreadyExistsException ignored) {
                try {
                    VariableSymbolTableItem symbolTableItem =
                            (VariableSymbolTableItem) SymbolTable.top.getItem(variableSymbolTableItem.getKey());
                    symbolTableItem.setType(variableSymbolTableItem.getType());
                } catch (ItemNotFoundException ignored1) {
                }
            }
            FunctionSymbolTableItem symbolTableItem = (FunctionSymbolTableItem)
                    SymbolTable.top.getItem(
                            FunctionSymbolTableItem.START_KEY + setGetVarDec.getVarName().getName());
            SymbolTable.push(symbolTableItem.getFunctionSymbolTable());
            retType.push(setGetVarDec.getVarType());
            for (VariableDeclaration arg : setGetVarDec.getArgs()) arg.accept(this);
            inSetter = true;
            inSetterGetter = true;
            setGetVarDec.getSetterBody().accept(this);
            inSetter = false;
            SymbolTable.pop();
            setGetVarDec.getGetterBody().accept(this);
            if (!haveReturn(setGetVarDec.getGetterBody()))
                setGetVarDec.addError(new MissingReturnStatement
                        (setGetVarDec.getGetterBody().getLine(), setGetVarDec.getVarName().getName()));
            inSetterGetter = false;
            retType.pop();
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public Void visit(AssignmentStmt assignmentStmt) {
        Type lValueType = assignmentStmt.getLValue().accept(expressionTypeChecker);
        Type rValueType = assignmentStmt.getRValue().accept(expressionTypeChecker);
        if (!expressionTypeChecker.isLvalue(assignmentStmt.getLValue()))
            assignmentStmt.addError(new LeftSideNotLvalue(assignmentStmt.getLine()));
        if (!this.expressionTypeChecker.isSameType(lValueType, rValueType))
            assignmentStmt.addError(new UnsupportedOperandType(assignmentStmt.getLine(),
                    BinaryOperator.assign.name()));
        return null;
    }

    @Override
    public Void visit(BlockStmt blockStmt) {
        for (Statement stmt : blockStmt.getStatements()) stmt.accept(this);
        return null;
    }

    @Override
    public Void visit(ConditionalStmt conditionalStmt) {
        Type conditionType = conditionalStmt.getCondition().accept(expressionTypeChecker);
        if (!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
            conditionalStmt.addError(new ConditionNotBool(conditionalStmt.getCondition().getLine()));
        SymbolTable.push(new SymbolTable(SymbolTable.top));
        conditionalStmt.getThenBody().accept(this);
        SymbolTable.pop();
        SymbolTable.push(new SymbolTable(SymbolTable.top));
        if (conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(FunctionCallStmt functionCallStmt) {
        expressionTypeChecker.setIsInFunctionCallStmt(true);
        functionCallStmt.getFunctionCall().accept(expressionTypeChecker);
        expressionTypeChecker.setIsInFunctionCallStmt(false);
        return null;
    }

    @Override
    public Void visit(DisplayStmt displayStmt) {
        Type argType = displayStmt.getArg().accept(expressionTypeChecker);
        if (!(argType instanceof BoolType) && !(argType instanceof IntType) && !(argType instanceof NoType)) // ListType
            displayStmt.addError(new UnsupportedTypeForDisplay(displayStmt.getArg().getLine()));
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        if (returnStmt.getReturnedExpr() != null) {
            Type ret = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
            boolean result = ret.getClass().equals(retType.peek().getClass());
            if (ret instanceof FptrType) result = expressionTypeChecker.isSameType(((FptrType) ret), retType.peek());
            if (!result && !inSetter && !(ret instanceof NoType) && !inMain)
                returnStmt.addError(new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine()));
            if (inSetter || inMain)
                returnStmt.addError(new CannotUseReturn(returnStmt.getLine()));
            if ((ret instanceof VoidType) && !(ret instanceof NoType))
                returnStmt.addError(new CantUseValueOfVoidFunction(returnStmt.getLine()));
        }
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);
        if (!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
            loopStmt.addError(new ConditionNotBool(loopStmt.getCondition().getLine()));
        SymbolTable.push(new SymbolTable(SymbolTable.top));
        loopStmt.getBody().accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(VarDecStmt varDecStmt) {
        int i = 0;
        for (VariableDeclaration varDec : varDecStmt.getVars()) {
            if (inSetterGetter && (i == 1)) return null;
            varDec.accept(this);
            i++;
        }
        return null;
    }

    @Override
    public Void visit(ListAppendStmt listAppendStmt) {
        listAppendStmt.getListAppendExpr().accept(expressionTypeChecker);
        return null;
    }

    @Override
    public Void visit(ListSizeStmt listSizeStmt) {
        listSizeStmt.getListSizeExpr().accept(expressionTypeChecker);
        return null;
    }
}
