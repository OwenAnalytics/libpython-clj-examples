(ns gigasquid.mxnet
  (:require [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py :refer [py. py.. py.-]]
            [clojure.string :as string]))

;;; sudo pip3 install mxnet
;;; sudo pip3 install opencv-python

(require-python '[mxnet :as mxnet])
(require-python '[mxnet.ndarray :as ndarray])
(require-python '[mxnet.module :as module])
(require-python '[mxnet.io :as io])
(require-python '[mxnet.test_utils :as test-utils])
(require-python '[mxnet.initializer :as initializer])
(require-python '[mxnet.metric :as metric])
(require-python '[mxnet.symbol :as sym])


;;; get the mnist data and format it

(def mnist (test-utils/get_mnist))
(def train-x (ndarray/array (py. (py/get-item mnist "train_data") "reshape" -1 784)))
(def train-y (ndarray/array (py/get-item mnist "train_label")))
(def test-x (ndarray/array (py. (py/get-item mnist "test_data") "reshape" -1 784)))
(def test-y (ndarray/array (py/get-item mnist "test_label")))

(def batch-size 100)

(def train-dataset (io/NDArrayIter :data train-x
                                   :label train-y
                                   :batch_size batch-size
                                   :shuffle true))
(def test-dataset (io/NDArrayIter :data test-x
                                  :label test-y
                                  :batch_size batch-size))


(def data-shapes (py.- train-dataset "provide_data"))
(def label-shapes (py.- train-dataset "provide_label"))

data-shapes ;=>  [DataDesc[data,(10, 784),<class 'numpy.float32'>,NCHW]]
label-shapes ;=> [DataDesc[softmax_label,(10,),<class 'numpy.float32'>,NCHW]]


;;;; Setting up the model and initializing it

(def data (sym/Variable "data"))

(def net (-> (sym/Variable "data")
             (sym/FullyConnected :name "fc1" :num_hidden 128)
             (sym/Activation :name "relu1" :act_type "relu")
             (sym/FullyConnected :name "fc2" :num_hidden 64)
             (sym/Activation :name "relu2" :act_type "relu")
             (sym/FullyConnected :name "fc3" :num_hidden 10)
             (sym/SoftmaxOutput :name "softmax")))



(def model (py/call-kw mxnet.module/Module [] {:symbol net :context (mxnet/cpu)}))
(py. model bind :data_shapes data-shapes :label_shapes label-shapes)
(py. model init_params)
(py. model init_optimizer :optimizer "adam")
(def acc-metric (mxnet.metric/Accuracy))


(defn end-of-data-error? [e]
  (string/includes? (.getMessage e) "StopIteration"))

(defn reset [iter]
  (py. iter reset))

(defn next-batch [iter]
  (try (py. iter next)
       (catch Exception e
         (when-not (end-of-data-error? e)
           (throw e)))))

(defn get-metric [metric]
  (py. metric get))

(defn train-epoch [model dataset metric]
  (reset dataset)
  (loop [batch (next-batch dataset)
         i 0]
    (if batch
      (do
        (py. model forward batch :is_train true)
        (py. model backward)
        (py. model update)
        (py. model update_metric metric (py/get-attr batch "label"))
        (when (zero? (mod i 100)) (println "i-" i " Training Accuracy " (py/$a metric get)))
        (recur (next-batch dataset) (inc i)))
      (println "Final Training Accuracy " (get-metric metric)))))

(defn test-accuracy [model dataset metric]
  (reset dataset)
  (loop [batch (next-batch dataset)
         i 0]
    (if batch
      (do
        (py. model forward batch)
        (py. model update_metric metric (py/get-attr batch "label"))
        (when (zero? (mod i 100)) (println "i-" i " Test Accuracy " (py/$a metric get)))
        (recur (next-batch dataset) (inc i)))
      (println "Final Test Accuracy " (get-metric metric)))))


(comment 


  ;;;training
  (dotimes [i 3]
    (println "========= Epoch " i  " ============")
    (train-epoch model train-dataset acc-metric))
  (get-metric acc-metric) ;=> ('accuracy', 0.9483555555555555)

  ;;;;
  (test-accuracy model test-dataset acc-metric)
  (get-metric acc-metric) ;=> ('accuracy', 0.9492052631578948)

  ;;visualization

  (py. train-dataset "reset")
  (def bd (next-batch train-dataset))
  (def data (first (py.- bd "data")))

  (def image (ndarray/slice data :begin 0 :end 1))
  (def image2 (py. image "reshape" [28 28]))
  (def image3 (-> (ndarray/multiply image2 256)
                  (ndarray/cast :dtype "uint8")))
  (def npimage (py. image3 "asnumpy"))


  (require-python '[cv2 :as cv2])
  (cv2/imwrite "number.jpg" npimage)


  )






