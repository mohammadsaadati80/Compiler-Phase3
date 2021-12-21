package main.visitor.type;

import main.ast.nodes.expression.*;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.types.Type;
import main.visitor.Visitor;

public class ExpressionTypeChecker extends Visitor<Type> {
    private final Graph<String> structHierarchy;
    private StructSymbolTableItem curStruct;
    private FunctionSymbolTableItem curFunction;
    private boolean isFunctioncallStmt;
    private boolean isMain;

    public ExpressionTypeChecker(Graph<String> structHierarchy) {
        this.classHierarchy = classHierarchy;
    }

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
        try{
            FunctionSymbolTableItem func = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + fptr.getFunctionName());
            return func;
        }catch (ItemNotFoundException e){
            return null;
        }
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
        if(el1 instanceof FptrType && el2 instanceof FptrType){
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
        }
        return false;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        Expression left = binaryExpression.getFirstOperand();
        Expression right = binaryExpression.getSecondOperand();
        Type tl = left.accept(this);
        Type tr = right.accept(this);
        BinaryOperator operator =  binaryExpression.getBinaryOperator();
        if (operator.equals(BinaryOperator.and) || operator.equals(BinaryOperator.or)){
            if (tl instanceof BoolType && tr instanceof BoolType)
                return new BoolType();
            if ((tl instanceof NoType || tl instanceof BoolType) &&
                    (tr instanceof BoolType || tr instanceof NoType))
                return new NoType();
        }
        else if(operator.equals(BinaryOperator.eq) || operator.equals(BinaryOperator.neq)){
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
        else if(operator.equals(BinaryOperator.gt) || operator.equals(BinaryOperator.lt)){
            if (tl instanceof IntType && tr instanceof IntType)
                return new BoolType();

            if ((tl instanceof NoType || tl instanceof IntType) &&
                    (tr instanceof IntType || tr instanceof NoType))
                return new NoType();
        }
        else if (operator.equals(BinaryOperator.append)) {
            if(tl instanceof ListType && sameType(((ListType) tl).getType(), tr)) {
                if (!((ListType) tl).isTypeSet() && ! (tr instanceof NoType)) {
                    ListType newList = new ListType(tr);
                    newList.setTypeSet(true);
                    return newList;
                }
                else {
                    ListType newList = new ListType(((ListType) tl).getType());
                    newList.setTypeSet(false);
                    return newList;
                }
            }
            if(tl instanceof NoType)
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
        Expression uExpr = unaryExpression.getOperand();
        Type ut = uExpr.accept(this);
        UnaryOperator operator = unaryExpression.getOperator();
        if(operator.equals(UnaryOperator.not)) {
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
        else {
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
    }

    @Override
    public Type visit(FunctionCall funcCall) {
        Type type = funcCall.getInstance().accept(this);
        boolean temp = isFunctioncallStmt;
        isFunctioncallStmt = false;
        ArrayList<Type> rtypes = new ArrayList<>();
        Map<String, Type> rtypesWithKey = new HashMap<>();
        for (Map.Entry<Identifier,Expression> argsWithKey: funcCall.getArgsWithKey().entrySet()) {
            Type curType = argsWithKey.getValue().accept(this);
            rtypesWithKey.put(argsWithKey.getKey().getName(), curType);
        }
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
            if(funcCall.getArgsWithKey().size() != 0) {
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
            }
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
            FunctionSymbolTableItem funcSym = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + identifier.getName());
            return new FptrType(identifier.getName());
        }catch (ItemNotFoundException e) {
            try{
                VariableSymbolTableItem varSym = (VariableSymbolTableItem) curFunction.getFunctionSymbolTable().getItem(VariableSymbolTableItem.START_KEY + identifier.getName());
                return varSym.getType();
            }catch (ItemNotFoundException e1) {}
        }
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
    public Type visit(StructAccess structAccess) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ListSize listSize) {
        Type instanceType = listSize.getInstance().accept(this);
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
    public Type visit(ListAppend listAppend) {
        //Todo
        return null;
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
