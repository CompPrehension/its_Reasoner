its_Reasoner - модуль системы, служащий для реализации вычислений по графам мыслительных процессов, построенных с
помощью [its_DomainModel](https://github.com/Max-Person/its_DomainModel/).

## Функционал в этом модуле

- Реализация вычислений LOQI-выражений.
- Реализация выполнения логики графов мыслительных процессов.
	- В т.ч. предоставление информации о пройденном пути в графе.

**Подробнее о функционале этого и других модулей читайте
на [вики](https://max-person.github.io/Compprehensive_ITS_wiki/2.-%D0%B2%D1%8B%D1%87%D0%B8%D1%81%D0%BB%D0%B5%D0%BD%D0%B8%D1%8F-(its_reasoner)/%D0%BE%D0%B1-its_reasoner.html).
**

## Установка зависимостей

Как автор данных проектов, **я рекомендую использовать Maven + [JitPack](https://jitpack.io/)  для подключения проектов
its_\* как зависимостей в ваших собственных проектах**. Для этого необходимо:

1\. В pom.xml своего проекта указать репозиторий JitPack:

```xml

<repositories>
	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>
```

2\. Также в pom.xml указать репозиторий как зависимость:

```xml

<dependency>
	<groupId>com.github.Max-Person</groupId>
	<artifactId>its_Reasoner</artifactId>
	<version>...</version>
</dependency>
```

- В качестве версии JitPack может принимать название ветки, тег (release tag), или хэш коммита. Для данных проектов я
  рекомендую указывать тег последней версии (см. [tags](https://github.com/Max-Person/its_DomainModel/tags)), чтобы ваш
  проект не сломался с обновлением библиотек.

3\. В IntelliJ IDEA надо обновить зависимости Maven (Maven -> Reload All Maven Projects), и все, данный проект настроен
для использования в качестве библиотеки.
> [!note]
> Обратите внимание, что JitPack собирает нужные артефакты только по запросу - т.е. когда вы подтягиваете зависимость. Это
> значит, что первое подобное подтягивание скорее всего займет несколько минут - JitPack-у нужно будет время на билд.  
> После завершения такого долгого билда, в IDEA может отобразиться надпись "Couldn't aqcuire locks", как будто произошла
> ошибка - в этом случае просто обновитесь еще раз, это будет быстро.

4\. Вместе с артефактами данной библиотеки всегда доступен ее исходный код, а в нем и документация (kotlindoc/javadoc).
**Проект на 90% задокументирован, поэтому смотрите на документацию к используемым вами методам!**  
Для того, чтобы исходный код и документация тоже подтянулись, нужно в IntelliJ IDEA сделать Maven -> Download Sources
and/or Documentation -> Download Sources and Documentation

## Примеры использования

Примеры использования описаны на Java, т.к. я думаю, что вы с большей вероятностью будете использовать именно ее (
использование на Kotlin в принципе аналогично, и более просто).

Данные примеры также полагаются на код из its_DomainModel, подробнее см. их примеры использования.

#### Создание учебной ситуации

```java
DomainModel situationModel=...;

		LearningSituation situation=new LearningSituation(
		situationModel,
		LearningSituation.collectDecisionTreeVariables(situationModel)   //мапа переменных дерева решений
		);
```  

#### Вычисление LOQI-выражения

```java
Operator expr=...;
		LearningSituation situation=...;

		Object result=expr.use(new DomainInterpreterReasoner(
		situation,
		new HashMap<>() //пустая мапа контекстных переменных  
		));
```  

#### Выполнений действий графа мыслительных процессов

```java
DomainSolvingModel model=...;
		LearningSituation situation=...;

		DecisionTreeTrace decisionTreeTrace=DecisionTreeReasoner.solve(
		model.getDecisionTree(),
		situation
		);
```

