package main.visitor.type;

import main.ast.nodes.Node;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.types.Type;
import main.ast.types.NoType;
import main.ast.types.FptrType;
import main.ast.types.ListType;
import main.ast.types.StructType;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.ast.types.primitives.VoidType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.visitor.Visitor;

import java.util.ArrayList;

public class ExpressionTypeChecker extends Visitor<Type> {

    private boolean isInFunctionCallStmt;
    private boolean seenNoneLvalue = false;

    public void setIsInFunctionCallStmt(boolean _isInFunctionCallStmt) {
        this.isInFunctionCallStmt = _isInFunctionCallStmt;
    }

    public boolean isSameType(Type element1, Type element2) {
        if (element1 instanceof NoType || element2 instanceof NoType)
            return true;
        if (element1 instanceof BoolType && element2 instanceof BoolType)
            return true;
        if (element1 instanceof IntType && element2 instanceof IntType)
            return true;
        if (element1 instanceof VoidType && element2 instanceof VoidType)
            return true;
        if (element1 instanceof StructType && element2 instanceof StructType) {
            StructType s1 = (StructType) element1;
            StructType s2 = (StructType) element2;
            return s1.getStructName().getName().equals(s2.getStructName().getName());
        }
        if (element1 instanceof ListType && element2 instanceof ListType) {
            return isSameType(((ListType) element1).getType(), ((ListType) element2).getType());
        }
        if (element1 instanceof FptrType && element2 instanceof FptrType) {
            Type element1ReturnType = (((FptrType) element1).getReturnType());
            Type element2ReturnType = (((FptrType) element2).getReturnType());
            if (!isSameType(element1ReturnType, element2ReturnType))
                return false;
            ArrayList<Type> element1ArgsTypes = new ArrayList<>(((FptrType) element1).getArgsType());
            ArrayList<Type> element2ArgsTypes = new ArrayList<>(((FptrType) element2).getArgsType());
            if (element1ArgsTypes.size() == 1)
                if (element1ArgsTypes.get(0) instanceof VoidType) element1ArgsTypes.clear();
            if (element2ArgsTypes.size() == 1)
                if (element2ArgsTypes.get(0) instanceof VoidType) element2ArgsTypes.clear();
            if (element1ArgsTypes.size() != element2ArgsTypes.size())
                return false;
            else {
                for (int i = 0; i < element1ArgsTypes.size(); i++) {
                    if (!isSameType(element1ArgsTypes.get(i), element2ArgsTypes.get(i)))
                        return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean isLvalue(Expression expression) {
        boolean previousSeenNoneLvalue = this.seenNoneLvalue;
        boolean previousIsCatchErrorsActive = Node.isCatchErrorsActive;
        this.seenNoneLvalue = false;
        Node.isCatchErrorsActive = false;
        expression.accept(this);
        boolean isLvalue = !this.seenNoneLvalue;
        Node.isCatchErrorsActive = previousIsCatchErrorsActive;
        this.seenNoneLvalue = previousSeenNoneLvalue;
        return isLvalue;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        this.seenNoneLvalue = true;
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        Expression leftOperand = binaryExpression.getFirstOperand();
        Expression rightOperand = binaryExpression.getSecondOperand();
        Type typeLeft = leftOperand.accept(this);
        Type typeRight = rightOperand.accept(this);
        if (operator.equals(BinaryOperator.eq)) {
            if (typeLeft instanceof ListType || typeRight instanceof ListType) {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(leftOperand.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            }
            if (!isSameType(typeLeft, typeRight)) {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(rightOperand.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else {
                if (typeLeft instanceof NoType || typeRight instanceof NoType)
                    return new NoType();
                else
                    return new BoolType();
            }
        } else if (operator.equals(BinaryOperator.gt) || operator.equals(BinaryOperator.lt)) {
            if (typeLeft instanceof NoType && typeRight instanceof NoType)
                return new NoType();
            else if ((typeLeft instanceof NoType && !(typeRight instanceof IntType))
                    || (typeRight instanceof NoType && !(typeLeft instanceof IntType))) {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (typeLeft instanceof NoType || typeRight instanceof NoType)
                return new NoType();
            if ((typeLeft instanceof IntType) && (typeRight instanceof IntType))
                return new BoolType();
        } else if (operator.equals(BinaryOperator.and) || operator.equals(BinaryOperator.or)) {
            if (typeLeft instanceof NoType && typeRight instanceof NoType)
                return new NoType();
            else if ((typeLeft instanceof NoType && !(typeRight instanceof BoolType))
                    || (typeRight instanceof NoType && !(typeLeft instanceof BoolType))) {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (typeLeft instanceof NoType || typeRight instanceof NoType)
                return new NoType();
            if ((typeLeft instanceof BoolType) && (typeRight instanceof BoolType))
                return new BoolType();
        } else if (operator.equals(BinaryOperator.add) || operator.equals(BinaryOperator.sub)
                || operator.equals(BinaryOperator.mult) || operator.equals(BinaryOperator.div)) {
            if (typeLeft instanceof NoType && typeRight instanceof NoType)
                return new NoType();
            else if ((typeLeft instanceof NoType && !(typeRight instanceof IntType)) ||
                    (typeRight instanceof NoType && !(typeLeft instanceof IntType))) {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (typeLeft instanceof NoType || typeRight instanceof NoType)
                return new NoType();
            if ((typeLeft instanceof IntType) && (typeRight instanceof IntType))
                return new IntType();
        } else if (operator.equals(BinaryOperator.assign)) {
            boolean isFirstLvalue = this.isLvalue(binaryExpression.getFirstOperand());
            if (!isFirstLvalue) {
                LeftSideNotLvalue exception = new LeftSideNotLvalue(binaryExpression.getLine());
                binaryExpression.addError(exception);
            }
            if (typeLeft instanceof NoType || typeRight instanceof NoType) {
                return new NoType();
            }
            boolean isSubtype = this.isSameType(typeRight, typeLeft);
            if (isSubtype) {
                if (isFirstLvalue)
                    return typeLeft;
                return new NoType();
            }
            UnsupportedOperandType exception =
                    new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
            binaryExpression.addError(exception);
            return new NoType();
        }
        UnsupportedOperandType exception = new UnsupportedOperandType(leftOperand.getLine(), operator.name());
        leftOperand.addError(exception);
        return new NoType();
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        this.seenNoneLvalue = true;
        UnaryOperator operator = unaryExpression.getOperator();
        Expression operandExpression = unaryExpression.getOperand();
        Type unaryType = operandExpression.accept(this);
        if (operator.equals(UnaryOperator.minus)) {
            if (unaryType instanceof IntType)
                return unaryType;
            if (unaryType instanceof NoType)
                return new NoType();
            else {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(operandExpression.getLine(), operator.name());
                operandExpression.addError(exception);
                return new NoType();
            }
        } else if (operator.equals(UnaryOperator.not)) {
            if (unaryType instanceof BoolType)
                return unaryType;
            if (unaryType instanceof NoType)
                return new NoType();
            else {
                UnsupportedOperandType exception =
                        new UnsupportedOperandType(operandExpression.getLine(), operator.name());
                operandExpression.addError(exception);
                return new NoType();
            }
        } else {
            boolean isOperandLvalue = this.isLvalue(unaryExpression.getOperand());
            if (unaryType instanceof NoType)
                return new NoType();
            if (unaryType instanceof IntType) {
                if (isOperandLvalue)
                    return unaryType;
                return new NoType();
            }
            UnsupportedOperandType exception = new UnsupportedOperandType(unaryExpression.getLine(), operator.name());
            unaryExpression.addError(exception);
            return new NoType();
        }
    }

    @Override
    public Type visit(FunctionCall funcCall) {
        seenNoneLvalue = true;
        Type retType = funcCall.getInstance().accept(this);
        if (!((retType instanceof FptrType) || (retType instanceof NoType))) {
            funcCall.addError(new CallOnNoneFptrType(funcCall.getLine()));
            return new NoType();
        }
        if (retType instanceof FptrType) {
            FptrType fptr = (FptrType) retType;
            if (fptr.getArgsType().size() == 1)
                if (fptr.getArgsType().get(0) instanceof VoidType) fptr.setArgsType(new ArrayList<>());
            boolean noType = false;
            if ((fptr.getReturnType() instanceof VoidType) && !isInFunctionCallStmt) {
                funcCall.addError(new CantUseValueOfVoidFunction(funcCall.getLine()));
                noType = true;
            }
            if (funcCall.getArgs().size() != fptr.getArgsType().size()) {
                funcCall.addError(new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine()));
                return new NoType();
            }
            for (int i = 0; (i < fptr.getArgsType().size()) && (i < funcCall.getArgs().size()); i++) {
                if (!isSameType(fptr.getArgsType().get(i), funcCall.getArgs().get(i).accept(this))) {
                    funcCall.addError(new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine()));
                    return new NoType();
                }
            }
            return noType ? new NoType() : fptr.getReturnType();
        }
        return new NoType();
    }

    @Override
    public Type visit(Identifier identifier) {
        try {
            SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + identifier.getName());
            return new StructType(identifier);
        } catch (ItemNotFoundException exception1) {
            try {
                FunctionSymbolTableItem funcSym = (FunctionSymbolTableItem)
                        SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + identifier.getName());
                ArrayList<Type> args = funcSym.getArgTypes();
                if (args.size() == 1) if (args.get(0) instanceof VoidType) args = new ArrayList<>();
                return new FptrType(args, funcSym.getReturnType());
            } catch (ItemNotFoundException exception2) {
                try {
                    SymbolTable.top.getItem(VariableSymbolTableItem.START_KEY + identifier.getName());
                    VariableSymbolTableItem varSym = (VariableSymbolTableItem)
                            SymbolTable.top.getItem(VariableSymbolTableItem.START_KEY + identifier.getName());
                    return varSym.getType();
                } catch (ItemNotFoundException exception3) {
                    VarNotDeclared exception = new VarNotDeclared(identifier.getLine(), identifier.getName());
                    identifier.addError(exception);
                    return new NoType();
                }
            }
        }
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type instanceType = listAccessByIndex.getInstance().accept(this);
        boolean previousSeenNoneLvalue = this.seenNoneLvalue;
        Type indexType = listAccessByIndex.getIndex().accept(this);
        this.seenNoneLvalue = previousSeenNoneLvalue;
        if (!(indexType instanceof IntType || indexType instanceof NoType)) {
            ListIndexNotInt exception = new ListIndexNotInt(listAccessByIndex.getLine());
            listAccessByIndex.addError(exception);
        }
        if (instanceType instanceof NoType)
            return new NoType();
        if (!(instanceType instanceof ListType)) {
            AccessByIndexOnNonList exception = new AccessByIndexOnNonList(listAccessByIndex.getLine());
            listAccessByIndex.addError(exception);
            return new NoType();
        } else {
            if (indexType instanceof IntType)
                return ((ListType) instanceType).getType();
            else
                return new NoType();
        }
    }

    @Override
    public Type visit(StructAccess structAccess) {
        Type instanceType = structAccess.getInstance().accept(this);
        if (instanceType instanceof StructType) {
            try {
                StructSymbolTableItem ss = (StructSymbolTableItem) SymbolTable
                        .root.getItem(StructSymbolTableItem.START_KEY
                                + ((StructType) instanceType).getStructName().getName());
                try {
                    VariableSymbolTableItem vs = (VariableSymbolTableItem)
                            ss.getStructSymbolTable().getItem(VariableSymbolTableItem.START_KEY
                                    + structAccess.getElement().getName());
                    return vs.getType();
                } catch (ItemNotFoundException exception) {
                    StructMemberNotFound ex = new StructMemberNotFound(structAccess.getLine(),
                            ((StructType) instanceType).getStructName().getName(),
                            structAccess.getElement().getName());
                    structAccess.addError(ex);
                    return new NoType();
                }
            } catch (ItemNotFoundException ignored) {
            }

        } else {
            if (!(instanceType instanceof NoType)) {
                AccessOnNonStruct exception = new AccessOnNonStruct(structAccess.getLine());
                structAccess.addError(exception);
            }
            return new NoType();
        }
        return new NoType();
    }

    @Override
    public Type visit(ListSize listSize) {
        this.seenNoneLvalue = true;
        Type argType = listSize.getArg().accept(this);
        if (argType instanceof ListType)
            return new IntType();
        else {
            if (!(argType instanceof NoType)) {
                GetSizeOfNonList exception = new GetSizeOfNonList(listSize.getLine());
                listSize.addError(exception);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ListAppend listAppend) {
        this.seenNoneLvalue = true;
        Type listArgType = listAppend.getListArg().accept(this);
        if (listArgType instanceof ListType) {
            Type elementArgType = listAppend.getElementArg().accept(this);
            if (isSameType(((ListType) listArgType).getType(), elementArgType))
                return new VoidType();
            else {
                if (!(elementArgType instanceof NoType)) {
                    NewElementTypeNotMatchListType exception =
                            new NewElementTypeNotMatchListType(listAppend.getLine());
                    listAppend.addError(exception);
                }
                return new NoType();
            }
        } else {
            if (!(listArgType instanceof NoType)) {
                AppendToNonList exception = new AppendToNonList(listAppend.getLine());
                listAppend.addError(exception);
            } else
                listAppend.getElementArg().accept(this);
            return new NoType();
        }
    }

    @Override
    public Type visit(ExprInPar exprInPar) {
        seenNoneLvalue = true;
        for (Expression input : exprInPar.getInputs()) {
            if (input instanceof Identifier) seenNoneLvalue = false;
            return input.accept(this);
        }
        return new NoType();
    }

    @Override
    public Type visit(IntValue intValue) {
        this.seenNoneLvalue = true;
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        this.seenNoneLvalue = true;
        return new BoolType();
    }
}