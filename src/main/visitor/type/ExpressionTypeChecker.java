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
import main.symbolTable.utils.graph.Graph;
import main.visitor.Visitor;

import java.util.ArrayList;
import java.util.Map;

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

    public StructSymbolTableItem getCurStruct() { return this.curStruct; }

    public FunctionSymbolTableItem getCurFunction() { return this.curFunction; }

    private FunctionSymbolTableItem findFunccSymobolTableItem(FptrType fptr) {
        /*try{
            FunctionSymbolTableItem func = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + fptr.getFunctionName());
            return func;
        }catch (ItemNotFoundException e){
            return null;
        }*/
        return null;

    }

    public boolean sameType(Type el1,Type el2){
        if(el1 instanceof NoType || el2 instanceof NoType)
            return true;
        if(el1 instanceof IntType  && el2 instanceof IntType)
            return true;
        if(el1 instanceof BoolType && el2 instanceof BoolType)
            return true;
        if(el1 instanceof StructType && el2 instanceof StructType)
            return true;
        if(el1 instanceof VoidType && el2 instanceof VoidType)
            return true;
        if (el1 instanceof ListType && el2 instanceof ListType){
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
        BinaryOperator operator =  binaryExpression.getBinaryOperator();
        if (operator == BinaryOperator.and || operator == BinaryOperator.or){
            if (tl instanceof BoolType && tr instanceof BoolType)
                return new BoolType();
            if ((tl instanceof NoType || tl instanceof BoolType) &&
                    (tr instanceof BoolType || tr instanceof NoType))
                return new NoType();
        }
        else if(operator == BinaryOperator.eq){
            if(tl instanceof ListType || tr instanceof ListType){
                UnsupportedOperandType exception = new UnsupportedOperandType(left.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            }
            if(!sameType(tl,tr)){
                UnsupportedOperandType exception = new UnsupportedOperandType(right.getLine(), operator.name());
                binaryExpression.addError(exception);
                return new NoType();
            }
            else {
                if(tl instanceof NoType || tr instanceof NoType)
                    return new NoType();
                else
                    return new BoolType();
            }
        }
        else if(operator == BinaryOperator.gt || operator == BinaryOperator.lt){
            if (tl instanceof IntType && tr instanceof IntType)
                return new BoolType();
            if ((tl instanceof NoType || tl instanceof IntType) && (tr instanceof IntType || tr instanceof NoType))
                return new NoType();
        }
        else if (operator == BinaryOperator.assign ) {
            boolean isFirstLvalue = this.isLvalue(binaryExpression.getFirstOperand());
            if(!isFirstLvalue) {
                LeftSideNotLvalue exception = new LeftSideNotLvalue(binaryExpression.getLine());
                binaryExpression.addError(exception);
            }
            if(tl instanceof NoType || tr instanceof NoType) {
                return new NoType();
            }
            boolean isSubtype = this.sameType(tr, tl);
            if(isSubtype) {
                if(isFirstLvalue)
                    return tl;
                return new NoType();
            }
            UnsupportedOperandType exception = new UnsupportedOperandType(binaryExpression.getLine(), operator.name());
            binaryExpression.addError(exception);
            return new NoType();
        }
        else {
            if (tl instanceof IntType && tr instanceof IntType)
                return new IntType();
            if ((tl instanceof NoType || tl instanceof IntType) &&
                    (tr instanceof IntType || tr instanceof NoType))
                return new NoType();
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
        if(operator == UnaryOperator.not) {
            if(ut instanceof BoolType)
                return new BoolType();
            if(ut instanceof NoType)
                return new NoType();
            else {
                UnsupportedOperandType exception = new UnsupportedOperandType(uExpr.getLine(), operator.name());
                uExpr.addError(exception);
                return new NoType();
            }
        }
        else if (operator == UnaryOperator.minus) {
            if (ut instanceof IntType)
                return new IntType();
            if(ut instanceof NoType)
                return new NoType();
            else{
                UnsupportedOperandType exception = new UnsupportedOperandType(uExpr.getLine(), operator.name());
                uExpr.addError(exception);
                return new NoType();
            }
        }
        else {
            boolean isOperandLvalue = this.isLvalue(unaryExpression.getOperand());
            if(ut instanceof NoType)
                return new NoType();
            if(ut instanceof IntType) {
                if(isOperandLvalue)
                    return ut;
                return new NoType();
            }
            UnsupportedOperandType exception = new UnsupportedOperandType(unaryExpression.getLine(), operator.name());
            unaryExpression.addError(exception);
            return new NoType();
        }
    }

    @Override
    public Type visit(FunctionCall funcCall) { // need check
        Type type = funcCall.getInstance().accept(this);
        boolean temp = isFunctioncallStmt;
        isFunctioncallStmt = false;
        ArrayList<Type> rtypes = new ArrayList<>();
        /*Map<String, Type> rtypesWithKey = new HashMap<>();
        for (Map.Entry<Identifier,Expression> argsWithKey: funcCall.getArgsWithKey().entrySet()) {
            Type curType = argsWithKey.getValue().accept(this);
            rtypesWithKey.put(argsWithKey.getKey().getName(), curType);
        }*/
        for (Expression expression : funcCall.getArgs()) {
            Type t = expression.accept(this);
            rtypes.add(t);
        }
        isFunctioncallStmt = temp;
        if (!(type instanceof FptrType || type instanceof NoType)){
            CallOnNoneFptrType exception = new CallOnNoneFptrType(funcCall.getLine());
            funcCall.addError(exception);
            return new NoType();
        }
        if (type instanceof FptrType) {
            boolean declareError = false;
            boolean error = false;
            FunctionSymbolTableItem func = findFunccSymobolTableItem((FptrType) type);
            if (func.getReturnType() instanceof VoidType && !isFunctioncallStmt) {
                CantUseValueOfVoidFunction exception = new CantUseValueOfVoidFunction(funcCall.getLine());
                funcCall.addError(exception);
                error = true;
            }
            /*if(funcCall.getArgsWithKey().size() != 0) {
                if(funcCall.getArgsWithKey().size() != func.getArgTypes().size()) {
                    ArgsInFunctionCallNotMatchDefinition exception = new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine());
                    funcCall.addError(exception);
                    error = true;
                }
            }
            else if(funcCall.getArgs().size() != func.getArgTypes().size())
                declareError = true;
            else {
                if(rtypes.size() != 0) {
                    int i = 0;
                    for (Map.Entry<String, Type> ltype : func.getArgTypes().entrySet()) {
                        if (!sameType(ltype.getValue(), rtypes.get(i))) {
                            declareError = true;
                            break;
                        }
                        i++;
                    }
                }
                if(rtypesWithKey.size() != 0) {
                    for (Map.Entry<String, Type> ltype : func.getArgTypes().entrySet()) {
                        if (rtypesWithKey.containsKey(ltype.getKey())) {
                            if (!sameType(rtypesWithKey.get(ltype.getKey()), ltype.getValue())) {
                                declareError = true;
                                break;
                            }
                        } else {
                            declareError = true;
                            break;
                        }
                    }
                }
            }*/
            if (declareError) {
                ArgsInFunctionCallNotMatchDefinition exception = new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine());
                funcCall.addError(exception);
            }
            if (declareError || error)
                return new NoType();
            else
                return func.getReturnType();
        }
        else
            return new NoType();
    }

    @Override
    public Type visit(Identifier identifier) {
        try {
            SymbolTableItem s = SymbolTable.root.getItem(StructSymbolTableItem.START_KEY+identifier.getName());
            System.out.println(s.getName()+" => "+identifier.getLine());
        } catch (ItemNotFoundException exception1) {
            try {
                SymbolTableItem s = SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY+identifier.getName());
                System.out.println(s.getName()+" => "+identifier.getLine());
            } catch (ItemNotFoundException exception2) {
                try {
                    SymbolTableItem s = SymbolTable.top.getItem(VariableSymbolTableItem.START_KEY+identifier.getName());
                    System.out.println(s.getName()+" => "+identifier.getLine());
                } catch (ItemNotFoundException exception3) {
                    System.out.println(identifier.getName()+ "^^^^^^^^^^^^^^^^^^^^^^^^^^^^"+identifier.getLine());
                }
            }
        }
        /*try {
            FunctionSymbolTableItem funcSym = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + identifier.getName());
            return new FptrType(identifier.getName());
        }catch (ItemNotFoundException e) {
            try{
                StructSymbolTableItem structSym = (StructSymbolTableItem) curStruct.getStructSymbolTable().getItem(StructSymbolTableItem.START_KEY + identifier.getName());
                return new StructType(identifier.getName());
            }catch (ItemNotFoundException e1) {
                try{
                    VariableSymbolTableItem varSym = (VariableSymbolTableItem) curFunction.getFunctionSymbolTable().getItem(VariableSymbolTableItem.START_KEY + identifier.getName());
                    return varSym.getType();
                }catch (ItemNotFoundException e2) {
                    VarNotDeclared exception = new VarNotDeclared(identifier.getLine(), identifier.getName());
                    identifier.addError(exception);
                    return new NoType();
                }
            }
        }*/
        return null;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type indexType = listAccessByIndex.getIndex().accept(this);
        Type instanceType = listAccessByIndex.getInstance().accept(this);
        if(!(indexType instanceof IntType || indexType instanceof NoType)){
            ListIndexNotInt exception = new ListIndexNotInt(listAccessByIndex.getLine());
            listAccessByIndex.addError(exception);
        }
        if(instanceType instanceof NoType)
            return new NoType();
        if(!(instanceType instanceof ListType)){
            AccessByIndexOnNonList exception = new AccessByIndexOnNonList(listAccessByIndex.getLine());
            listAccessByIndex.addError(exception);
            return new NoType();
        }
        else {
            if (indexType instanceof IntType)
                return ((ListType) instanceType).getType();
            else
                return new NoType();
        }
    }

    @Override
    public Type visit(StructAccess structAccess) {  // need check
        Type instanceType = structAccess.getInstance().accept(this); // need check
        if(instanceType instanceof StructType)
            return new NoType(); // need check
        else {
            if(!(instanceType instanceof NoType)) {
                AccessOnNonStruct exception = new AccessOnNonStruct(structAccess.getLine());
                structAccess.addError(exception);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ListSize listSize) {
        Type instanceType = listSize.getArg().accept(this);
        if(instanceType instanceof ListType)
            return new IntType();
        else {
            if(!(instanceType instanceof NoType)) {
                GetSizeOfNonList exception = new GetSizeOfNonList(listSize.getLine());
                listSize.addError(exception);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ListAppend listAppend) { // need check
        Type instanceType = listAppend.getListArg().accept(this);
        if(instanceType instanceof ListType)
            return new VoidType();
        else {
            if(!(instanceType instanceof NoType)) {
                AppendToNonList exception = new AppendToNonList(listAppend.getLine());
                listAppend.addError(exception);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ExprInPar exprInPar) {
        //Todo
        return null;
    }

    @Override
    public Type visit(IntValue intValue) {
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        return new BoolType();
    }
}
