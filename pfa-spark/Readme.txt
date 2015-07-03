mvn clean package
mvn --offline -DskipTests=true clean package
mvn --offline -DskipTests=true clean install

#######################################################################################################################
export HADOOP_CONF_DIR=/home/marcoz/Projects/BC/IT/bc-it-admin/calvalus/config/cv2/conf-common
# unset when doing local tasks again

export SPARK_HOME=/home/marcoz/Projects/BC/spark/spark-1.2.0-bin-hadoop2.4
jars=$(echo /home/marcoz/Projects/BC/PFA/esa-pfa/pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT-jobdir/*.jar | tr ' ' ',')

local, a single product
=======================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master local \
    --jars ${jars} \
    --executor-memory 2G \
    pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
    "Algal Bloom Detection" \
    "file:///home/marcoz/Scratch/OC_MERIS_aday/03/MER_RR__1PRACR20080103_111541_000026162064_00438_30552_0000.N1" \
    /home/marcoz/Scratch/pfa/spark/2-local-single-product

local, a 5 days
=======================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master local[4] \
    --jars ${jars} \
    pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
    "Algal Bloom Detection" \
    "file:///home/marcoz/Scratch/OC_MERIS_aday/*/*.N1" \
    /home/marcoz/Scratch/pfa/spark/local-5days


YARN on our Calvalus Cluster
============================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master yarn-cluster \
    --executor-memory 1500M \
    --num-executors 300 \
    --conf spark.yarn.queue=lc \
    --jars ${jars} \
    pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
    "Algal Bloom Detection" \
    "hdfs://calvalus/calvalus/eodata/MER_RR__1P/r03/2003/*/*/*.N1" \
    hdfs://calvalus/calvalus/home/marcoz/PFA/yarn-2003

$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master yarn-cluster \
    --executor-memory 1500M \
    --num-executors 300 \
    --conf spark.yarn.queue=lc \
    --jars ${jars} \
    pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
    "Algal Bloom Detection" \
    "hdfs://calvalus//calvalus/eodata/MER_RR__1P/r03/2013/12/*/*.N1" \
    hdfs://calvalus/calvalus/home/marcoz/PFA/yarn-2013-12


$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master yarn-cluster \
    --executor-memory 1500M \
    --num-executors 300 \
    --conf spark.yarn.queue=lc \
    --jars ${jars} \
    pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
    "Algal Bloom Detection" \
    "hdfs://calvalus//calvalus/eodata/MER_RR__1P/r03/*/*/*/*.N1" \
    hdfs://calvalus/calvalus/home/marcoz/PFA/yarn-full-mission

$SPARK_HOME/bin/spark-submit \
        --class org.esa.pfa.spark.ExtractFexApp \
        --master yarn-cluster \
        --executor-memory 1500M \
        --num-executors 300 \
        --conf spark.yarn.queue=lc \
        --jars ${jars} \
        pfa-spark/target/pfa-spark-2.0.0-SNAPSHOT.jar \
        "Algal Bloom Detection" \
        "hdfs://calvalus//calvalus/eodata/MER_RR__1P/r03/*/*/*/*.N1" \
        hdfs://calvalus/calvalus/home/marcoz/PFA/yarn-full-mission