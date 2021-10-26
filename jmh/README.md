# JMH 简介
[JMH](https://github.com/openjdk/jmh) (Java Microbenchmark Harness)是用于代码微基准测试的工具套件，主要是基于方法层面的基准测试，精度可以达到纳秒级。

该工具是由 Oracle 内部实现 JIT 的大牛们编写的，他们应该比任何人都了解 JIT 以及 JVM 对于基准测试的影响。当你定位到热点方法，希望进一步优化方法性能的时候，就可以使用 JMH 对优化的结果进行量化的分析。

JMH 比较典型的应用场景如下：
1. 想准确地知道某个方法需要执行多长时间，以及执行时间和输入之间的相关性
2. 对比接口不同实现在给定条件下的吞吐量
3. 查看多少百分比的请求在多长时间内完成

# 使用教程
## 加入依赖
因为 JMH 是 JDK9 自带的，如果是 JDK9 之前的版本需要加入如下依赖（目前 JMH 的最新版本为 1.33）：
```maven
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.33</version>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.33</version>
</dependency>
```
## 编写基准测试
创建一个 JMH 测试类，用来判断 + 和 StringBuilder.append() 两种字符串拼接哪个耗时更短，具体代码如下所示：
```java
//使用模式 默认是Mode.Throughput
@BenchmarkMode(Mode.AverageTime)
// 配置预热次数，默认是每次运行1秒，运行10次，这里设置为3次
@Warmup(iterations = 3, time = 1)
// 本例是一次运行4秒，总共运行3次，在性能对比时候，采用默认1秒即可
@Measurement(iterations = 3, time = 4)
// 配置同时起多少个线程执行
@Threads(1)
//代表启动多个单独的进程分别测试每个方法，这里指定为每个方法启动一个进程
@Fork(1)
// 定义类实例的生命周期，Scope.Benchmark：所有测试线程共享一个实例，用于测试有状态实例在多线程共享下的性能
@State(value = Scope.Benchmark)
// 统计结果的时间单元
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class JmhTest {

    @Param(value = {"10", "50", "100"})
    private int length;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JmhTest.class.getSimpleName())
                .result("result.json")
                .resultFormat(ResultFormatType.JSON).build();
        new Runner(opt).run();
    }

    @Benchmark
    public void testStringBufferAdd(Blackhole blackhole) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            sb.append(i);
        }
        blackhole.consume(sb.toString());
    }

    @Benchmark
    public void testStringBuilderAdd(Blackhole blackhole) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i);
        }
        blackhole.consume(sb.toString());
    }
}
```
上面介绍概念时已经提到Benchmark为基准测试，在使用中只需对要测试的方法添加@Benchmark注解即可。而在测试类JmhTest指定测试的预热、线程、测试维度等信息。

main方法中通过OptionsBuilder构造测试配置对象Options，并传入Runner，启动测试。这里指定测试结果为json格式，同时会将结果存储在result.json文件当中。

## 执行测试
执行main方法，控制台首先会打印出如下信息：
```
# JMH version: 1.21
# VM version: JDK 1.8.0_291, Java HotSpot(TM) 64-Bit Server VM, 25.291-b10
# VM invoker: /Library/Java/JavaVirtualMachines/jdk1.8.0_291.jdk/Contents/Home/jre/bin/java
# VM options: -javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=61602:/Applications/IntelliJ IDEA.app/Contents/bin -Dfile.encoding=UTF-8
# Warmup: 3 iterations, 1 s each
# Measurement: 3 iterations, 4 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Average time, time/op
# Benchmark: com.clay.JmhTest.testStringBufferAdd
# Parameters: (length = 10)
```
这些信息主要用来展示测试的基本信息，包括jdk、JVM、预热配置、执行轮次、执行时间、执行线程、测试的统计单位等。
```
# Run progress: 0.00% complete, ETA 00:01:30
# Fork: 1 of 1
# Warmup Iteration   1: 158.999 ns/op
# Warmup Iteration   2: 102.049 ns/op
# Warmup Iteration   3: 83.162 ns/op
```
这是对待测试方法的预热处理，这部分不会记入测试结果。预热主要让JVM对被测代码进行足够多的优化，比如JIT编译器的优化。
```
Iteration   1: 59.312 ns/op
Iteration   2: 62.679 ns/op
Iteration   3: 70.925 ns/op

Result "com.clay.JmhTest.testStringBufferAdd":
  64.305 ±(99.9%) 109.003 ns/op [Average]
  (min, avg, max) = (59.312, 64.305, 70.925), stdev = 5.975
  CI (99.9%): [≈ 0, 173.308] (assumes normal distribution)
```
显示每次（共3次）迭代执行速率，最后进行统计。这里是对testStringBuilderAdd方法执行length为100的测试，通过 (min, avg, max) 三项可以看出最小时间、平均时间、最大时间的值，单位为ns。stdev显示的是误差时间。

通常情况下，我们只用看最后的结果即可：
```
Benchmark                     (length)  Mode  Cnt     Score      Error  Units
JmhTest.testStringBufferAdd         10  avgt    3    64.305 ±  109.003  ns/op
JmhTest.testStringBufferAdd         50  avgt    3   647.631 ± 1376.014  ns/op
JmhTest.testStringBufferAdd        100  avgt    3  1473.937 ± 2057.345  ns/op
JmhTest.testStringBuilderAdd        10  avgt    3    89.874 ±   28.098  ns/op
JmhTest.testStringBuilderAdd        50  avgt    3   582.965 ±  996.058  ns/op
JmhTest.testStringBuilderAdd       100  avgt    3  1302.931 ± 2853.303  ns/op
```
看到上述结果我们可能会很吃惊，我们知道StringBuffer要比StringBuilder的性能低一些，但结果发现它们的之间的差别并不是很大。这是因为JIT编译器进行了优化，比如当JVM发现在测试当中StringBuffer并没有发生逃逸，于是就进行了锁消除操作。

## 常用注解
下面对JHM当中常用的注解进行说明，以便大家可以更精确的使用。

### @BenchmarkMode
配置Mode选项，作用于类或者方法上，其value属性为Mode数组，可同时支持多种Mode，如：@BenchmarkMode({Mode.SampleTime, Mode.AverageTime})，也可设为Mode.All，即全部执行一遍。

org.openjdk.jmh.annotations.Mode为枚举类，对应的源代码如下：
```java
public enum Mode {
    Throughput("thrpt", "Throughput, ops/time"),
    AverageTime("avgt", "Average time, time/op"),
    SampleTime("sample", "Sampling time"),
    SingleShotTime("ss", "Single shot invocation time"),
    All("all", "All benchmark modes");
    // 省略其他内容
}
```
不同模式之间，测量的维度或测量的方式不同。目前JMH共有四种模式：
* Throughput：整体吞吐量，例如“1秒内可以执行多少次调用”，单位为ops/time； 
* AverageTime：调用的平均时间，例如“每次调用平均耗时xxx毫秒”，单位为time/op； 
* SampleTime：随机取样，最后输出取样结果的分布，，例如“99%的调用在xxx毫秒以内，99.99%的调用在xxx毫秒以内”； 
* SingleShotTime：以上模式都是默认一次iteration是1s，只有SingleShotTime是只运行一次。往往同时把warmup次数设为0，用于测试冷启动时的性能； 
* All：上面的所有模式都执行一次；

### @Warmup
在执行@Benchmark之前进行预热操作，确保测试的准确性，可用于类或者方法上。默认是每次运行1秒，运行10次。

其中@Warmup有以下属性：

* iterations：预热的次数；Iteration是JMH进行测试的最小单位，在大部分模式下，一次iteration代表的是一秒，JMH会在这一秒内不断调用需要benchmark的方法，然后根据模式对其采样，计算吞吐量，计算平均执行时间等。 
* time：每次预热的时间； 
* timeUnit：时间的单位，默认秒； 
* batchSize：批处理大小，每次操作调用几次方法；

JIT在执行的过程中会将热点代码编译为机器码，并进行各种优化，从而提高执行效率。预热的主要目的是让JVM的JIT机制生效，让结果更接近真实效果。

### @State
类注解，JMH测试类必须使用@State注解，不然会提示无法运行。

State定义了一个类实例的生命周期（作用范围），可以类比Spring Bean的Scope。因为很多benchmark会需要一些表示状态的类，JMH会根据scope来进行实例化和共享操作。

@State可以被继承使用，如果父类定义了该注解，子类则无需定义。

由于JMH允许多线程同时执行测试，不同的选项含义如下：

* Scope.Thread：默认的State，该状态为每个线程独享，每个测试线程分配一个实例； 
* Scope.Benchmark：该状态在所有线程间共享，所有测试线程共享一个实例，用于测试有状态实例在多线程共享下的性能； 
* Scope.Group：该状态为同一个组里面所有线程共享。

### @OutputTimeUnit
benchmark统计结果所使用的时间单位，可用于类或者方法注解，使用java.util.concurrent.TimeUnit中的标准时间单位。

### @Measurement
度量，其实就是实际调用方法所需要配置的一些基本测试参数，可用于类或者方法上。配置属性项目和作用与@Warmup相同。

一般比较重的程序可以进行大量的测试，放到服务器上运行。在性能对比时，采用默认1秒即可，如果用jvisualvm做性能监控，可以指定一个较长时间运行。

### @Threads
每个进程中同时起多少个线程执行，可用于类或者方法上。默认值是Runtime.getRuntime().availableProcessors()，根据具体情况选择，一般为cpu乘以2。

### @Fork
代表启动多个单独的进程分别测试每个方法，可用于类或者方法上。如果fork数是2的话，则JMH会fork出两个进程来进行测试。

JVM因为使用了profile-guided optimization而“臭名昭著”，这对于微基准测试来说十分不友好，因为不同测试方法的profile混杂在一起，“互相伤害”彼此的测试结果。对于每个@Benchmark方法使用一个独立的进程可以解决这个问题，这也是JMH的默认选项。注意不要设置为0，设置为n则会启动n个进程执行测试（似乎也没有太大意义）。fork选项也可以通过方法注解以及启动参数来设置。

### @Param
属性级注解，指定某项参数的多种情况，特别适合用来测试一个函数在不同的参数输入的情况下的性能，只能作用在字段上，使用该注解必须定义@State注解。

@Param注解接收一个String数组，在@Setup方法执行前转化为对应的数据类型。多个@Param注解的成员之间是乘积关系，譬如有两个用@Param注解的字段，第一个有5个值，第二个字段有2个值，那么每个测试方法会跑5*2=10次。

### @Benchmark
方法注解，表示该方法是需要进行benchmark的对象，用法和JUnit的@Test类似。

### @Setup
方法注解，这个注解的作用就是我们需要在测试之前进行一些准备工作，比如对一些数据的初始化之类的。

### @TearDown
方法注解，与@Setup相对的，会在所有benchmark执行结束以后执行，比如关闭线程池，数据库连接等的，主要用于资源的回收等。

### @Threads
每个fork进程使用多少个线程去执行测试方法，默认值是Runtime.getRuntime().availableProcessors()。

### @Group
方法注解，可以把多个benchmark定义为同一个group，则它们会被同时执行，譬如用来模拟生产者－消费者读写速度不一致情况下的表现。

### @Level
用于控制@Setup，@TearDown的调用时机，默认是Level.Trial。

* Trial：每个benchmark方法前后； 
* Iteration：每个benchmark方法每次迭代前后； 
* Invocation：每个benchmark方法每次调用前后，谨慎使用，需留意javadoc注释；

# JMH注意事项
## 无用代码消除（Dead Code Elimination）
现代编译器是十分聪明的，它们会对代码进行推导分析，判定哪些代码是无用的然后进行去除，这种行为对微基准测试是致命的，它会使你无法准确测试出你的方法性能。

JMH本身已经对这种情况做了处理，要记住：1.永远不要写void方法；2.在方法结束返回计算结果。有时候如果需要返回多于一个结果，可以考虑自行合并计算结果，或者使用JMH提供的BlackHole对象：
```java
/*
 * This demonstrates Option A:
 *
 * Merge multiple results into one and return it.
 * This is OK when is computation is relatively heavyweight, and merging
 * the results does not offset the results much.
 */
@Benchmark
public double measureRight_1() {
    return Math.log(x1) + Math.log(x2);
}
/*
 * This demonstrates Option B:
 *
 * Use explicit Blackhole objects, and sink the values there.
 * (Background: Blackhole is just another @State object, bundled with JMH).
 */
@Benchmark
public void measureRight_2(Blackhole bh) {
    bh.consume(Math.log(x1));
    bh.consume(Math.log(x2));
}
```

再比如下面代码：
```java
@Benchmark
public void testStringAdd(Blackhole blackhole) {
    String a = "";
    for (int i = 0; i < length; i++) {
        a += i;
    }
}
```
JVM可能会认为变量a从来没有使用过，从而进行优化把整个方法内部代码移除掉，这就会影响测试结果。

JMH提供了两种方式避免这种问题，一种是将这个变量作为方法返回值return a，一种是通过Blackhole的consume来避免JIT 的优化消除。

## 常量折叠（Constant Folding）
常量折叠是一种现代编译器优化策略，例如，i = 320 * 200 * 32，多数的现代编译器不会真的产生两个乘法的指令再将结果储存下来，取而代之的，它们会辨识出语句的结构，并在编译时期将数值计算出来（i = 2,048,000）。

在微基准测试中，如果你的计算输入是可预测的，也不是一个@State实例变量，那么很可能会被JIT给优化掉。对此，JMH的建议是：1.永远从@State实例中读取你的方法输入；2.返回你的计算结果；3.或者考虑使用BlackHole对象；

见如下官方例子：
```java
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class JMHSample_10_ConstantFold {
    private double x = Math.PI;
    private final double wrongX = Math.PI;
    @Benchmark
    public double baseline() {
        // simply return the value, this is a baseline
        return Math.PI;
    }
    @Benchmark
    public double measureWrong_1() {
        // This is wrong: the source is predictable, and computation is foldable.
        return Math.log(Math.PI);
    }
    @Benchmark
    public double measureWrong_2() {
        // This is wrong: the source is predictable, and computation is foldable.
        return Math.log(wrongX);
    }
    @Benchmark
    public double measureRight() {
        // This is correct: the source is not predictable.
        return Math.log(x);
    }
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMHSample_10_ConstantFold.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(5)
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
```

## 循环展开（Loop Unwinding）
循环展开最常用来降低循环开销，为具有多个功能单元的处理器提供指令级并行。也有利于指令流水线的调度。例如：
```java
for (i = 1; i <= 60; i++) 
   a[i] = a[i] * b + c;
```
可以展开成：
```java
for (i = 1; i <= 60; i+=3){
  a[i] = a[i] * b + c;
  a[i+1] = a[i+1] * b + c;
  a[i+2] = a[i+2] * b + c;
}
```
由于编译器可能会对你的代码进行循环展开，因此JMH建议不要在你的测试方法中写任何循环。如果确实需要执行循环计算，可以结合@BenchmarkMode(Mode.SingleShotTime)和@Measurement(batchSize = N)来达到同样的效果。参考如下例子：
```java
/*
 * Suppose we want to measure how much it takes to sum two integers:
 */
int x = 1;
int y = 2;
/*
 * This is what you do with JMH.
 */
@Benchmark
@OperationsPerInvocation(100)
public int measureRight() {
    return (x + y);
}
```

# JMH可视化
在示例的main方法中指定了生成测试结果的输出文件result.json，其中的内容就是控制台输出的相关内容以json格式存储。

针对json格式的内容，可以在其他网站上以图表的形式可视化展示。

对应网站，[JMH Visual Chart](http://deepoove.com/jmh-visual-chart) 或者 [Visualizer](https://jmh.morethan.io)。

# 生成jar包执行
对于大型的测试，一般会放在Linux服务器里去执行。JMH官方提供了生成jar包的方式来执行，在maven里增加如下插件：
```maven
<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>2.4.1</version>
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>shade</goal>
                </goals>
                <configuration>
                    <finalName>jmh-demo</finalName>
                    <transformers>
                        <transformer
                                implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                            <mainClass>org.openjdk.jmh.Main</mainClass>
                        </transformer>
                    </transformers>
                </configuration>
            </execution>
        </executions>
    </plugin>
</plugins>
```
执行maven的命令生成可执行jar包，并执行：
```
mvn clean package
java -jar target/jmh-demo.jar JmhTest
```

# 参考
* https://openjdk.java.net/projects/code-tools/jmh/
* https://www.zhihu.com/question/276455629/answer/1259967560
