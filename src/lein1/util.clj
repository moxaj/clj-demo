(ns lein1.util
  (:require [overtone.at-at :as at-at])
  (:import [com.badlogic.gdx Gdx Input InputAdapter Input$Keys])
  (:gen-class))

;;;; ========================================================================================
;;;; Java interop macro

(defmacro set-all!
  [object & args]
  (let [object-sym (gensym)]
    `(let [~object-sym ~object]
       ~@(map (fn [[field value]]
                `(set! (. ~object-sym ~field) ~value))
              (partition 2 args))
       ~object-sym)))

;;;; ========================================================================================
;;;; Conversion to bytes

(defn int->byte-vector
  [i]
  (into [] (.. java.nio.ByteBuffer (allocate 4) (putInt i) array)))

(defn float->byte-vector
  [f]
  (into [] (.. java.nio.ByteBuffer (allocate 4) (putFloat f) array)))

(defn boolean-vector->byte
  [b]
  (if (every? false? b)
    (byte 0)
    (let [bs (java.util.BitSet. 8)]
      (doall (map-indexed #(if %2
                             (.set bs (- 7 %1))
                             (.clear bs (- 7 %1))) b))
      (first (into [] (.toByteArray bs))))))

;;;; ========================================================================================
;;;; Conversion from bytes

(defn byte-vector->int
  [b]
  (.. java.nio.ByteBuffer (wrap (into-array Byte/TYPE b)) getInt))

(defn byte-vector->float
  [b]
  (.. java.nio.ByteBuffer (wrap (into-array Byte/TYPE b)) getFloat))

(defn byte->boolean-vector
  [b]
  (for [i (range 7 -1 -1)]
    (bit-test b i)))

;;;; ========================================================================================
;;;; Threshold functions

(defn compare-floats
  [f1 f2]
  (> (Math/abs (float (- f1 f2)))
     (float 0.01)))

;;;; ========================================================================================
;;;; Seq manipulation

(defn map-vals
  [m f]
  (into {} (for [[k v] m]
             [k (f v)])))

(defn split-at-multiple
  [coll lengths]
  (loop [c coll
         l lengths
         result []]
    (if (empty? l)
      result
      (let [[l1 & l2] l
            [c1 c2] (if (= :rem l1)
                      [c []]
                      (split-at l1 c))]
        (recur c2 l2 (conj result c1))))))

;;;; ========================================================================================
;;;; Loops

(def loop-pool (at-at/mk-pool))

(defn create-loop
  [function interval]
  (atom {:function function :interval interval :scheduled-fn nil}))

(defn start-loop
  [l]
  (swap! l #(assoc % :scheduled-fn (at-at/every (:interval %)
                                                (:function %)
                                                loop-pool))))

(defn stop-loop
  [l]
  (swap! l #(let [scheduled-fn (:scheduled-fn %)]
              (at-at/stop scheduled-fn)
              (assoc % :scheduled-fn nil))))
