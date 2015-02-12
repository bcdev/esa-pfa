mvn clean package
mvn --offline -DskipTests=true clean package

#######################################################################################################################
export HADOOP_CONF_DIR=/home/marcoz/Projects/BC/IT/bc-it-admin/calvalus/config/cv2/conf-common
# unset when doing local tasks again

export SPARK_HOME=/home/marcoz/Projects/BC/spark/spark-1.2.0-bin-hadoop2.4
jars=$(echo /home/marcoz/Projects/BC/PFA/esa-pfa/spark-spike/target/spark-spike-1.0-jobdir/*.jar | tr ' ' ',')

local, a single product
=======================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master local \
    --jars ${jars} \
    target/spark-spike-1.0.jar \
    "Algal Bloom Detection" \
    "file:///home/marcoz/Scratch/OC_MERIS_aday/03/MER_RR__1PRACR20080103_111541_000026162064_00438_30552_0000.N1" \
    /home/marcoz/Scratch/pfa/spark/local-single-product

local, a 5 days
=======================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master local[4] \
    --jars ${jars} \
    target/spark-spike-1.0.jar \
    "Algal Bloom Detection" \
    "file:///home/marcoz/Scratch/OC_MERIS_aday/*/*.N1" \
    /home/marcoz/Scratch/pfa/spark/local-5days


YARN on our Calvalus Cluster
============================
$SPARK_HOME/bin/spark-submit \
    --class org.esa.pfa.spark.ExtractFexApp \
    --master yarn-cluster \
    --executor-memory 2G \
    --num-executors 20 \
    --conf spark.yarn.queue=cc \
    --jars ${jars} \
    target/spark-spike-1.0.jar \
    "Algal Bloom Detection" \
    "hdfs://master00:9000/calvalus/eodata/MER_RR__1P/r03/2003/12/*/*.N1" \
    hdfs://master00:9000/calvalus/home/marcoz/PFA/yarn-1month



