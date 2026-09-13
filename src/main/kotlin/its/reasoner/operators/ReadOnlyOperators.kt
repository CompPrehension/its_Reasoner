package its.reasoner.operators

import its.model.expressions.Operator
import its.model.expressions.literals.BooleanLiteral
import its.model.expressions.literals.ClassLiteral
import its.model.expressions.literals.DecisionTreeVarLiteral
import its.model.expressions.literals.DoubleLiteral
import its.model.expressions.literals.EnumLiteral
import its.model.expressions.literals.IntegerLiteral
import its.model.expressions.literals.ObjectLiteral
import its.model.expressions.literals.StringLiteral
import its.model.expressions.literals.VariableLiteral
import its.model.expressions.operators.AddNewObject
import its.model.expressions.operators.AddRelationshipLink
import its.model.expressions.operators.AssignDecisionTreeVar
import its.model.expressions.operators.AssignProperty
import its.model.expressions.operators.Block
import its.model.expressions.operators.CallProcedure
import its.model.expressions.operators.Cast
import its.model.expressions.operators.CheckClass
import its.model.expressions.operators.CheckRelationship
import its.model.expressions.operators.Compare
import its.model.expressions.operators.CompareWithComparisonOperator
import its.model.expressions.operators.ExistenceQuantifier
import its.model.expressions.operators.ForAllQuantifier
import its.model.expressions.operators.GetByCondition
import its.model.expressions.operators.GetByRelationship
import its.model.expressions.operators.GetClass
import its.model.expressions.operators.GetExtreme
import its.model.expressions.operators.GetPropertyValue
import its.model.expressions.operators.GetRelationshipParamValue
import its.model.expressions.operators.IfThen
import its.model.expressions.operators.LogicalAnd
import its.model.expressions.operators.LogicalNot
import its.model.expressions.operators.LogicalOr
import its.model.expressions.operators.RemoveRelationshipLink

/**
 * Классификация операторов по влиянию на состояние вычисления
 * (модель предметной области и переменные дерева решений).
 *
 * Оператор, не попавший ни в один из списков, считается изменяющим состояние:
 * так новые операторы по умолчанию не попадают под кэширование,
 * пока их не отнесут к одной из групп осознанно.
 */
object ReadOnlyOperators {

    /**
     * Операторы, которые только читают модель и переменные
     */
    @JvmField
    val READ_ONLY: Set<Class<out Operator>> = setOf(
        Block::class.java,
        IfThen::class.java,
        Compare::class.java,
        CompareWithComparisonOperator::class.java,
        GetByCondition::class.java,
        GetExtreme::class.java,
        GetClass::class.java,
        GetPropertyValue::class.java,
        GetByRelationship::class.java,
        GetRelationshipParamValue::class.java,
        Cast::class.java,
        CheckClass::class.java,
        CheckRelationship::class.java,
        ExistenceQuantifier::class.java,
        ForAllQuantifier::class.java,
        LogicalAnd::class.java,
        LogicalNot::class.java,
        LogicalOr::class.java,
        VariableLiteral::class.java,
        DecisionTreeVarLiteral::class.java,
        ClassLiteral::class.java,
        ObjectLiteral::class.java,
        BooleanLiteral::class.java,
        IntegerLiteral::class.java,
        DoubleLiteral::class.java,
        StringLiteral::class.java,
        EnumLiteral::class.java,
    )

    /**
     * Операторы, изменяющие модель или переменные дерева решений
     * (в том числе вызовы процедур, чьи побочные эффекты неизвестны)
     */
    @JvmField
    val MUTATING: Set<Class<out Operator>> = setOf(
        AssignProperty::class.java,
        AssignDecisionTreeVar::class.java,
        AddRelationshipLink::class.java,
        RemoveRelationshipLink::class.java,
        AddNewObject::class.java,
        CallProcedure::class.java,
    )

    /**
     * Состоит ли выражение целиком из операторов, не изменяющих состояние вычисления
     */
    @JvmStatic
    fun isReadOnly(operator: Operator): Boolean {
        return operator.javaClass in READ_ONLY && operator.children.all { isReadOnly(it) }
    }
}
