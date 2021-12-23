package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.types.NoType;
import main.ast.types.Type;
import main.ast.types.primitives.BoolType;
import main.compileError.typeError.*;
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;

import java.util.Stack;

public class TypeChecker extends Visitor<Void> {
    ExpressionTypeChecker expressionTypeChecker;
    private Graph<String> structHierarchy;
    private final Stack<Type> retType = new Stack<>();
    private boolean inSetter;
    private boolean inSetterGetter;

    public void TypeChecker(Graph<String> structHierarchy) {
        this.structHierarchy = structHierarchy;
        this.expressionTypeChecker = new ExpressionTypeChecker(structHierarchy);
    }

    @Override
    public Void visit(Program program) {
        for (StructDeclaration structDeclaration : program.getStructs())
            structDeclaration.accept(this);
        for (FunctionDeclaration functionDeclaration : program.getFunctions())
            functionDeclaration.accept(this);
        program.getMain().accept(this);
        return null;
    }

    @Override
    public Void visit(FunctionDeclaration functionDec) {
        retType.push(functionDec.getReturnType());
        for (VariableDeclaration arg : functionDec.getArgs()) arg.accept(this);
        functionDec.getBody().accept(this);
        retType.pop();
        return null;
    }

    @Override
    public Void visit(MainDeclaration mainDec) {
        mainDec.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDec) {
        if (inSetterGetter) {
            variableDec.addError(new CannotUseDefineVar(variableDec.getLine()));
            return null;
        }
        if (variableDec.getDefaultValue() != null) variableDec.getDefaultValue().accept(this);
        return null;
    }

    @Override
    public Void visit(StructDeclaration structDec) {
        structDec.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(SetGetVarDeclaration setGetVarDec) {
        for (VariableDeclaration varDec : setGetVarDec.getArgs()) varDec.accept(this);
        retType.push(setGetVarDec.getVarType());
        inSetter = true;
        inSetterGetter = true;
        setGetVarDec.getSetterBody().accept(this);
        inSetter = false;
        setGetVarDec.getGetterBody().accept(this);
        inSetterGetter = false;
        retType.pop();
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
        if(!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
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
        //Todo
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {
        Type ret = returnStmt.getReturnedExpr().accept(expressionTypeChecker);
        boolean result = ret.getClass().equals(retType.peek().getClass());
        if (!result && !inSetter)
            returnStmt.addError(new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine()));
        if (inSetter)
            returnStmt.addError(new CannotUseReturn(returnStmt.getLine()));
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);
        if(!(conditionType instanceof BoolType) && !(conditionType instanceof NoType))
            loopStmt.addError(new ConditionNotBool(loopStmt.getCondition().getLine()));
        loopStmt.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(VarDecStmt varDecStmt) {
        //Todo
        return null;
    }

    @Override
    public Void visit(ListAppendStmt listAppendStmt) {
        //Todo
        return null;
    }

    @Override
    public Void visit(ListSizeStmt listSizeStmt) {
        //Todo
        return null;
    }
}
