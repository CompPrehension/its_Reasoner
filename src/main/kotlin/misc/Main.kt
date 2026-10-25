package misc

import its.model.DomainSolvingModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.reasoner.LearningSituation
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import java.io.File

/**
 * Пример использования библиотеки для решения задач, описанных в формате its_DomainModel
 */
fun main(args: Array<String>) { //путь к папке с данными
    val dir = "D:\\RDF Benchmark Test\\BESDUI-master\\Benchmark\\playground"

    //Создать модель домена
    val model = DomainSolvingModel(dir, DomainSolvingModel.BuildMethod.LOQI)

    val situationDomain = DomainLoqiBuilder.buildDomain(File("D:\\RDF Benchmark Test\\BESDUI-master\\Datasets\\bsbm.loqi").bufferedReader())

    //Получить полное описание ситуации и провалидировать его
    situationDomain.add(model.domainModel)
    situationDomain.validateAndThrow()

    val situation = LearningSituation(situationDomain)
    //Решение задачи
    val result = model.decisionTree("1").solve(situation)

}
