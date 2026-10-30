package com.uteexpress;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.uteexpress", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule orderEntitiesHaveNoApplicationDependencies = noClasses()
            .that().resideInAPackage("com.uteexpress.order.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..repository..", "..service..", "com.uteexpress.security..",
                    "org.springframework.transaction..", "org.springframework.context..");

    @ArchTest
    static final ArchRule controllersUseServicesAndDtos = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..entity..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnWeb = noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "org.springframework.web..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule repositoriesDoNotDependOnUpperLayers = noClasses().that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
            .allowEmptyShould(true); // Repository implementations start in later tasks.

    @ArchTest
    static final ArchRule dtosDoNotExposeEntities = noClasses().that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..entity..", "..repository..", "..service..", "..controller..");

    @ArchTest
    static final ArchRule modulesHaveNoCycles = slices().matching("com.uteexpress.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule moduleInternalsArePrivate = classes().should(new ArchCondition<>(
            "access other business modules only through service and DTO contracts") {
        @Override
        public void check(JavaClass source, ConditionEvents events) {
            for (var dependency : source.getDirectDependenciesFromSelf()) {
                String from = source.getPackageName();
                String to = dependency.getTargetClass().getPackageName();
                if (!from.startsWith("com.uteexpress.") || !to.startsWith("com.uteexpress.")) {
                    continue;
                }
                String fromModule = from.split("\\.")[2];
                String toModule = to.split("\\.")[2];
                if (fromModule.equals(toModule) || toModule.equals("common")) {
                    continue;
                }
                boolean allowed = !fromModule.equals("common")
                        && (to.startsWith("com.uteexpress." + toModule + ".service")
                        || to.startsWith("com.uteexpress." + toModule + ".dto")
                        // SEC-01's public identity contracts live at the module root.
                        || java.util.Set.of("com.uteexpress.security.CurrentUserProvider",
                                "com.uteexpress.security.CurrentUser")
                                .contains(dependency.getTargetClass().getName()));
                events.add(new SimpleConditionEvent(dependency, allowed, dependency.getDescription()));
            }
        }
    });
}
