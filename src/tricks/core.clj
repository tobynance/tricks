(ns tricks.core
  (:require tricks.server
            [clojure.tools.cli :refer [parse-opts]])
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
(def cli-options
  ;; An option with a required argument
  [["-s" "--max-score SCORE" "The max score for a client to reach before the program ends"
    :default 100
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn usage [options-summary]
  (->> ["Usage: program-name [options] action"
        ""
        "Options:"
        options-summary]
       (clojure.string/join \newline)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn exit [status msg]
  (println msg)
  (System/exit status))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors))
      :else
      (tricks.server/run [
                           ;["Clojure1" ["java" "-jar" "clojure_client-0.1.0-SNAPSHOT-standalone.jar" "Clojure1"] (java.io.File. "clients")]
                           ["Python_1" ["python" "simple_client.py" "Python_1"] (java.io.File. "clients")]
                           ["Python_4" ["python" "simple_client.py" "Python_4"] (java.io.File. "clients")]
                           ["Python_2" ["python" "simple_client.py" "Python_2"] (java.io.File. "clients")]
                           ["Python_3" ["python" "simple_client.py" "Python_3"] (java.io.File. "clients")]]
                         (:max-score options)))))
