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
import main.symbolTable.items.SymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.visitor.Visitor;

import java.util.ArrayList;

public class ExpressionTypeChecker extends Visitor<Type> {
    private StructSymbolTableItem curStruct;
    private FunctionSymbolTableItem curFunction;
    private boolean isFunctioncallStmt;
    private boolean isMain;
    private boolean seenNoneLvalue = false;

    public void setFunctioncallStmt(boolean isFunctioncallStmt) {
        this.isFunctioncallStmt = isFunctioncallStmt;
    }

    public void setCurStruct(StructSymbolTableItem curStruct) {
        this.curStruct = curStruct;
    }

    public void setCurFunction(FunctionSymbolTableItem curFunction) {
        this.curFunction = curFunction;
    }

    public StructSymbolTableItem getCurStruct() {
        return this.curStruct;
    }

    public FunctionSymbolTableItem getCurFunction() {
        return this.curFunction;
    }

    private FunctionSymbolTableItem findFunccSymobolTableItem(FptrType fptr) {
        /*try{
            FunctionSymbolTableItem func = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + fptr.getFunctionName());
            return func;
        }catch (ItemNotFoundException e){
            return null;
        }*/
        return null;

    }

    public boolean sameType(Type el1, Type el2) {
        if (el1 instanceof NoType || el2 instanceof NoType)
            return true;
        if (el1 instanceof IntType && el2 instanceof IntType)
            return true;
        if (el1 instanceof BoolType && el2 instanceof BoolType)
            return true;
        if (el1 instanceof StructType && el2 instanceof StructType)
            return true;
        if (el1 instanceof VoidType && el2 instanceof VoidType)
            return true;
        if (el1 instanceof ListType && el2 instanceof ListType) {
            return sameType(((ListType) el1).getType(), ((ListType) el2).getType());
        }
        /*if(el1 instanceof FptrType && el2 instanceof FptrType){
            if(((FptrType) el1).getFunctionName().equals(((FptrType) el2).getFunctionName()))
                return true;
            FunctionSymbolTableItem f1 = findFunccSymobolTableItem((FptrType) el1);
            FunctionSymbolTableItem f2 = findFunccSymobolTableItem((FptrType) el2);
            Type el1RetType = f1.getReturnType();
            Type el2RetType = f2.getReturnType();
            if(!sameType(el1RetType,el2RetType))
                return false;
            ArrayList<Type> el1ArgsTypes = new ArrayList<>(f1.getArgTypes().values());
            ArrayList<Type> el2ArgsTypes = new ArrayList<>(f2.getArgTypes().values());
            if(el1ArgsTypes.size() != el2ArgsTypes.size())
                return false;
            else{
                for(int i = 0; i < el1ArgsTypes.size(); i++){
                    if(!sameType(el1ArgsTypes.get(i),el2ArgsTypes.get(i)))
                        return false;
                }
            }
            return true;
        }*/
        return false;
    }

    public boolean isLvalue(Expression expression) {
        boolean prevIsCatchErrorsActive = Node.isCatchErrorsActive;
        boolean prevSeenNoneLvalue = this.seenNoneLvalue;
        Node.isCatchErrorsActive = false;
        this.seenNoneLvalue = false;
        expression.accept(this);
        boolean isLvalue = !this.seenNoneLvalue;
        this.seenNoneLvalue = prevSeenNoneLvalue;
        Node.isCatchErrorsActive = prevIsCatchErrorsActive;
        return isLvalue;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        this.seenNoneLvalue = true;
        Expression left = binaryExpression.getFirstOperand();
        Expression right = binaryExpression.getSecondOperand();
        Type tl = left.accept(this);
        Type tr = right.accept(this);
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        if (operator.equals(BinaryOperator.and) || operator.equals(BinaryOperator.or)) {
            if (tl instanceof NoType && tr instanceof NoType)
                return new NoType();
            else if ((tl instanceof NoType && !(tr instanceof BoolType)) || (tr instanceof NoType && !(tl instanceof BoolType))) {
                UnsupportedOperandType exception = new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (tl instanceof NoType || tr instanceof NoType)
                return new NoType();
            if ((tl instanceof BoolType) && (tr instanceof BoolType))
                return new BoolType();
//            if (tl instanceof BoolType && tr instanceof BoolType)
//                return new BoolType();
//            if ((tl instanceof NoType || tl instanceof BoolType) &&
//                    (tr instanceof BoolType || tr instanceof NoType))
//                return new NoType();
        } else if (operator.equals(BinaryOperator.eq)) { // need check
            if (tl instanceof ListType || tr instanceof ListType) {
                UnsupportedOperandType exception = new UnsupportedOperandType(left.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            }
            if (!sameType(tl, tr)) {
                UnsupportedOperandType exception = new UnsupportedOperandType(right.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else {
                if (tl instanceof NoType || tr instanceof NoType)
                    return new NoType();
                else
                    return new BoolType();
            }
        } else if (operator.equals(BinaryOperator.gt) || operator.equals(BinaryOperator.lt)) {
            if (tl instanceof NoType && tr instanceof NoType)
                return new NoType();
            else if ((tl instanceof NoType && !(tr instanceof IntType)) || (tr instanceof NoType && !(tl instanceof IntType))) {
                UnsupportedOperandType exception = new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (tl instanceof NoType || tr instanceof NoType)
                return new NoType();
            if ((tl instanceof IntType) && (tr instanceof IntType))
                return new BoolType();
//            if (tl instanceof IntType && tr instanceof IntType)
//                return new BoolType();
//            if ((tl instanceof NoType || tl instanceof IntType) && (tr instanceof IntType || tr instanceof NoType))
//                return new NoType();
        } else if (operator.equals(BinaryOperator.assign)) {
            boolean isFirstLvalue = this.isLvalue(binaryExpression.getFirstOperand());
            if (!isFirstLvalue) {
                LeftSideNotLvalue exception = new LeftSideNotLvalue(binaryExpression.getLine());
                binaryExpression.addError(exception);
            }
            if (tl instanceof NoType || tr instanceof NoType) {
                return new NoType();
            }
            boolean isSubtype = this.sameType(tr, tl);
            if (isSubtype) {
                if (isFirstLvalue)
                    return tl;
                return new NoType();
            }
            UnsupportedOperandType exception = new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
            binaryExpression.addError(exception);
            return new NoType();
        } else { // + - / *
            if (tl instanceof NoType && tr instanceof NoType)
                return new NoType();
            else if ((tl instanceof NoType && !(tr instanceof IntType)) ||
                    (tr instanceof NoType && !(tl instanceof IntType))) {
                UnsupportedOperandType exception = new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            } else if (tl instanceof NoType || tr instanceof NoType)
                return new NoType();
            if ((tl instanceof IntType) && (tr instanceof IntType))
                return new IntType();
//            if (tl instanceof IntType && tr instanceof IntType)
//                return new IntType();
//            if ((tl instanceof NoType || tl instanceof IntType) && (tr instanceof IntType || tr instanceof NoType))
//                return new NoType();
        }
        UnsupportedOperandType exception = new UnsupportedOperandType(left.getLine(), operator.name());
        left.addError(exception);
        return new NoType();
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        this.seenNoneLvalue = true;
        Expression uExpr = unaryExpression.getOperand();
        Type ut = uExpr.accept(this);
        UnaryOperator operator = unaryExpression.getOperator();
        if (operator.equals(UnaryOperator.not)) {
            if (ut instanceof BoolType)
                return ut;
            if (ut instanceof NoType)
                return new NoType();
            else {
                UnsupportedOperandType exception = new UnsupportedOperandType(uExpr.getLine(), operator.name());
                uExpr.addError(exception);
                return new NoType();
            }
        } else if (operator.equals(UnaryOperator.minus)) {
            if (ut instanceof IntType)
                return ut;
            if (ut instanceof NoType)
                return new NoType();
            else {
                UnsupportedOperandType exception = new UnsupportedOperandType(uExpr.getLine(), operator.name());
                uExpr.addError(exception);
                return new NoType();
            }
        } else {
            boolean isOperandLvalue = this.isLvalue(unaryExpression.getOperand());
            if (ut instanceof NoType)
                return new NoType();
            if (ut instanceof IntType) {
                if (isOperandLvalue)
                    return ut;
                return new NoType();
            }
            UnsupportedOperandType exception = new UnsupportedOperandType(unaryExpression.getLine(), operator.name());
            unaryExpression.addError(exception);
            return new NoType();
        }
    }

    @Override
    public Type visit(FunctionCall funcCall) {
        Type retType = funcCall.getInstance().accept(this);
        if (!(retType instanceof FptrType)) {
            funcCall.addError(new CallOnNoneFptrType(funcCall.getLine()));
            return new NoType();
        }
        FptrType fptr = (FptrType) retType;
        if ((fptr.getReturnType() instanceof VoidType) && !isFunctioncallStmt)
            funcCall.addError(new CantUseValueOfVoidFunction(funcCall.getLine()));
        if (funcCall.getArgs().size() != fptr.getArgsType().size()) {
            funcCall.addError(new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine()));
            return fptr.getReturnType();
        }
        for (int i = 0; (i < fptr.getArgsType().size()) && (i < funcCall.getArgs().size()); i++) {
            if (!fptr.getArgsType().get(i).getClass().equals(funcCall.getArgs().get(i).accept(this).getClass())){
                funcCall.addError(new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine()));
                return fptr.getReturnType();
            }
        }
        return fptr.getReturnType();
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
                return new FptrType(funcSym.getArgTypes(), funcSym.getReturnType());
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
        boolean prevSeenNoneLvalue = this.seenNoneLvalue;
        Type indexType = listAccessByIndex.getIndex().accept(this);
        this.seenNoneLvalue = prevSeenNoneLvalue;
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
                StructSymbolTableItem ss = (StructSymbolTableItem) SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + ((StructType) instanceType).getStructName().getName());
                try {
                    VariableSymbolTableItem vs = (VariableSymbolTableItem) ss.getStructSymbolTable().getItem(VariableSymbolTableItem.START_KEY + structAccess.getElement().getName());
                    return vs.getType();
                } catch (ItemNotFoundException exception) {
                    StructMemberNotFound ex = new StructMemberNotFound(structAccess.getLine(), ((StructType) instanceType).getStructName().getName(), structAccess.getElement().getName());
                    structAccess.addError(ex);
                    return new NoType();
                }
            } catch (ItemNotFoundException exception1) {
                return null;
            }

        } else {
            if (!(instanceType instanceof NoType)) {
                AccessOnNonStruct exception = new AccessOnNonStruct(structAccess.getLine());
                structAccess.addError(exception);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ListSize listSize) {
        this.seenNoneLvalue = true;
        Type instanceType = listSize.getArg().accept(this);
        if (instanceType instanceof ListType)
            return new IntType();
        else {
            if (!(instanceType instanceof NoType)) {
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
            if (sameType(((ListType) listArgType).getType(), elementArgType))
                return new VoidType();
            else {
                if (!(elementArgType instanceof NoType)) {
                    NewElementTypeNotMatchListType exception = new NewElementTypeNotMatchListType(listAppend.getLine());
                    listAppend.addError(exception);
                }
                return new VoidType();
            }
        } else {
            if (!(listArgType instanceof NoType)) {
                AppendToNonList exception = new AppendToNonList(listAppend.getLine());
                listAppend.addError(exception);
            } else
                listAppend.getElementArg().accept(this);
            return new VoidType();
        }
    }

    @Override
    public Type visit(ExprInPar exprInPar) {
        for (Expression input : exprInPar.getInputs()) {
            return input.accept(this);
        }
        return null;
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
