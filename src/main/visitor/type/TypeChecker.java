package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.types.ListType;
import main.ast.types.NoType;
import main.ast.types.StructType;
import main.ast.types.Type;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemAlreadyExistsException;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.SymbolTableItem;
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
        retType.pop();
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(MainDeclaration mainDec) {
        SymbolTable.push(new SymbolTable());
        mainDec.getBody().accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDec) {
        if (inSetterGetter) variableDec.addError(new CannotUseDefineVar(variableDec.getLine()));
        VariableSymbolTableItem variableSymbolTableItem = new VariableSymbolTableItem(variableDec.getVarName());
        variableSymbolTableItem.setType(variableDec.getVarType());
        if (variableDec.getVarType() instanceof StructType){
            try {
                SymbolTable.root.getItem(StructSymbolTableItem.START_KEY+variableDec.getVarName().getName());
            }catch (ItemNotFoundException exception){
                StructType structType = (StructType)variableDec.getVarType();
                variableDec.addError(new StructNotDeclared(variableDec.getLine(),structType.getStructName().getName()));
            }
        }
        try {
            SymbolTable.top.put(variableSymbolTableItem);
        } catch (ItemAlreadyExistsException ignored) {
        }
        if (variableDec.getDefaultValue() != null) variableDec.getDefaultValue().accept(this);
        return null;
    }

    @Override
    public Void visit(StructDeclaration structDec) {
        try {
            StructSymbolTableItem symbolTableItem = (StructSymbolTableItem)
                    SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + structDec.getStructName().getName());
            SymbolTable.push(symbolTableItem.getStructSymbolTable());
            structDec.getBody().accept(this);
        } catch (ItemNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public Void visit(SetGetVarDeclaration setGetVarDec) {
        try {
            FunctionSymbolTableItem symbolTableItem = (FunctionSymbolTableItem)
                    SymbolTable.top.getItem(FunctionSymbolTableItem.START_KEY + setGetVarDec.getVarName().getName());
            SymbolTable.push(symbolTableItem.getFunctionSymbolTable());
            retType.push(setGetVarDec.getVarType());
            inSetter = true;
            inSetterGetter = true;
            setGetVarDec.getSetterBody().accept(this);
            inSetter = false;
            SymbolTable.pop(); // TODO maybe not need to pop
            setGetVarDec.getGetterBody().accept(this);
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
        if (!this.expressionTypeChecker.sameType(lValueType, rValueType))
            assignmentStmt.addError(new UnsupportedOperandType(assignmentStmt.getLine(), BinaryOperator.assign.name()));
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
        conditionalStmt.getThenBody().accept(this);
        if (conditionalStmt.getElseBody() != null)
            conditionalStmt.getElseBody().accept(this);
        return null;
    }

    @Override
    public Void visit(FunctionCallStmt functionCallStmt) {
        //Todo
        return null;
    }

    @Override
    public Void visit(DisplayStmt displayStmt) {
        Type argType = displayStmt.getArg().accept(expressionTypeChecker);
        if (!(argType instanceof BoolType) && !(argType instanceof IntType) && !(argType instanceof ListType))
            displayStmt.addError(new UnsupportedTypeForDisplay(displayStmt.getArg().getLine()));
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        Type ret = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        boolean result = ret.getClass().equals(retType.peek().getClass());
        if (!result && !inSetter)
            returnStmt.addError(new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine()));
        if (inSetter || inMain)
            returnStmt.addError(new CannotUseReturn(returnStmt.getLine()));
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);
        if (!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
            loopStmt.addError(new ConditionNotBool(loopStmt.getCondition().getLine()));
        loopStmt.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(VarDecStmt varDecStmt) {
        for (VariableDeclaration varDec : varDecStmt.getVars()) varDec.accept(this);
        return null;
    }

    @Override
    public Void visit(ListAppendStmt listAppendStmt) {
        //Todo
//        listAppendStmt.
        return null;
    }

    @Override
    public Void visit(ListSizeStmt listSizeStmt) {
        if (!(listSizeStmt.getListSizeExpr().accept(expressionTypeChecker) instanceof ListType))
            listSizeStmt.addError(new GetSizeOfNonList(listSizeStmt.getLine()));
        return null;
    }
}
