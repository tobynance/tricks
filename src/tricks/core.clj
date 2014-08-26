(ns tricks.core
  [:require tricks.server]
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use 'clojure.reflect)
(defn all-methods
  [x]
    (->> x reflect
           :members
           (filter :return-type)
           (map :name)
           sort
           (map #(str "." %) )
           distinct
           println))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  [& args]
  (tricks.server/run [
                      ;["Clojure1" ["java" "-jar" "clojure_client-0.1.0-SNAPSHOT-standalone.jar" "Clojure1"] (java.io.File. "clients")]
                      ["Python_1" ["python" "simple_client.py" "Python_1"] (java.io.File. "clients")]
                      ["Python_4" ["python" "simple_client.py" "Python_4"] (java.io.File. "clients")]
                      ["Python_2" ["python" "simple_client.py" "Python_2"] (java.io.File. "clients")]
                      ["Python_3" ["python" "simple_client.py" "Python_3"] (java.io.File. "clients")]]))
