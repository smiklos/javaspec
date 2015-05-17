package info.javaspec.runner.ng;

import com.google.common.collect.Lists;
import de.bechte.junit.runners.context.HierarchicalContextRunner;
import info.javaspec.runner.ng.NewJavaSpecRunner.NoExamplesException;
import info.javaspecproto.ContextClasses;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.mockito.Mockito;

import static info.javaspec.testutil.Assertions.capture;
import static info.javaspec.testutil.Matchers.matchesRegex;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(HierarchicalContextRunner.class)
public class NewJavaSpecRunnerTest {
  private final NewExampleGateway gateway = mock(NewExampleGateway.class);
  private Runner subject;

  public class constructor {
    public class givenAClassWithoutAnyExamples {
      @Test
      public void throwsNoExamplesException() throws Exception {
        givenGatewayWithNoExamples(ContextClasses.Empty.class);
        Exception ex = capture(NoExamplesException.class, () -> new NewJavaSpecRunner(gateway));
        assertThat(ex.getMessage(),
          matchesRegex("^Context class .*[$]Empty must contain at least 1 example in an It field$"));
      }
    }
  }

  public class getDescription {
    private Description description;

    public class givenALeafContextClassWith1Example {
      @Before
      public void setup() {
        givenGatewayWithFlatContext(ContextClasses.OneIt.class, "only_test");
        subject = new NewJavaSpecRunner(gateway);
        description = subject.getDescription();
      }

      @Test
      public void returnsATestDescriptionForTheGivenClass() throws Exception {
        assertTestDescription(description, "^.*ContextClasses[$]OneIt$", "only_test");
      }
    }

    public class givenAContextClassWith2OrMoreExamples {
      @Before
      public void setup() {
        givenGatewayWithFlatContext(ContextClasses.TwoIt.class, "first_test", "second_test");
        subject = new NewJavaSpecRunner(gateway);
        description = subject.getDescription();
      }

      @Test
      public void returnsASuiteDescriptionForTheGivenClass() {
        assertSuiteDescription(description, "^.*ContextClasses[$]TwoIt$");
      }

      @Test
      public void containsChildTestDescriptionsForEachExampleInTheGivenClass() {
        assertThat(description.getChildren().stream().map(Description::getClassName).collect(toList()),
          contains(matchesRegex("^.*ContextClasses[$]TwoIt$"), matchesRegex("^.*ContextClasses[$]TwoIt$")));
        assertThat(description.getChildren().stream().map(Description::getMethodName).collect(toList()),
          contains("first_test", "second_test"));
      }
    }

    public class givenANonLeafContextClass {
      @Before
      public void setup() {
        givenGatewayWithNestedContext(
          ContextClasses.NestedContext.class, ContextClasses.NestedContext.inner.class,
          "asserts"
        );
        subject = new NewJavaSpecRunner(gateway);
        description = subject.getDescription();
      }

      @Test
      public void returnsASuiteDescriptionHierarchyMatchingTheContextClassHierarchy() throws Exception {
        assertSuiteDescription(description, "^.*ContextClasses[$]NestedContext$");
        assertThat(description.getChildren(), hasSize(1));
      }

      @Test
      public void returnsTestDescriptionsWhereTestsAreDeclared() throws Exception {
        Description test = description.getChildren().get(0);
        assertTestDescription(test, "^.*ContextClasses[$]NestedContext[$]inner$", "asserts");
        assertThat(test.getChildren(), hasSize(0));
      }
    }

    private void assertSuiteDescription(Description description, String contextNamePattern) {
      assertThat(description.isSuite(), equalTo(true));
      assertThat(description.isTest(), equalTo(false));
      assertThat(description.getClassName(), matchesRegex(contextNamePattern));
    }

    private void assertTestDescription(Description description, String contextNamePattern, String testName) {
      assertThat(description.isSuite(), equalTo(false));
      assertThat(description.isTest(), equalTo(true));
      assertThat(description.getClassName(), matchesRegex(contextNamePattern));
      assertThat(description.getMethodName(), equalTo(testName));
    }
  }

  public class testCount {
    public class givenAClassWith1OrMoreExamples {
      @Test
      public void returnsTheNumberOfTestsInTheGivenContextClass() throws Exception {
        givenGatewayWithFlatContext(ContextClasses.TwoIt.class, "first_test", "second_test");
        subject = new NewJavaSpecRunner(gateway);
        assertThat(subject.testCount(), equalTo(2));
      }
    }
  }

  private void givenGatewayWithNoExamples(Class<?> context) {
    when(gateway.hasExamples()).thenReturn(false);
    when(gateway.totalNumExamples()).thenReturn(0);

    Mockito.<Class<?>>when(gateway.rootContextClass()).thenReturn(context);
    when(gateway.exampleFieldNames(context)).thenReturn(Lists.newArrayList());
    when(gateway.subContextClasses(context)).thenReturn(Lists.newArrayList());
  }

  private void givenGatewayWithFlatContext(Class<?> context, String... exampleNames) {
    when(gateway.hasExamples()).thenReturn(true);
    when(gateway.totalNumExamples()).thenReturn(exampleNames.length);

    Mockito.<Class<?>>when(gateway.rootContextClass()).thenReturn(context);
    when(gateway.exampleFieldNames(context)).thenReturn(Lists.newArrayList(exampleNames));
    when(gateway.subContextClasses(context)).thenReturn(Lists.newArrayList());
  }

  private void givenGatewayWithNestedContext(Class<?> outer, Class<?> inner, String exampleName) {
    when(gateway.hasExamples()).thenReturn(true);
    when(gateway.totalNumExamples()).thenReturn(1);

    Mockito.<Class<?>>when(gateway.rootContextClass()).thenReturn(outer);
    when(gateway.exampleFieldNames(outer)).thenReturn(Lists.newArrayList());
    when(gateway.subContextClasses(outer)).thenReturn(Lists.newArrayList(inner));

    when(gateway.exampleFieldNames(inner)).thenReturn(Lists.newArrayList(exampleName));
    when(gateway.subContextClasses(inner)).thenReturn(Lists.newArrayList());
  }
}