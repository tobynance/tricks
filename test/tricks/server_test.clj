(ns tricks.server-test
  (:require [clojure.test :refer :all]
            [tricks.server :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-test-client-list
  []
  [{:name "a" :score 0} {:name "b" :score 1} {:name "c" :score 2}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-test-clients
  []
  (clients-to-client-map (get-test-client-list)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest clients-to-client-map-test
  (testing "clients-to-client-map"
    (let [clients-map (get-test-clients)]
      (is (= 3 (count clients-map)))
      (is (= (get clients-map "a") {:name "a" :score 0}))
      (is (= (get clients-map "b") {:name "b" :score 1}))
      (is (= (get clients-map "c") {:name "c" :score 2})))))


;(defn update-play-order
;  [name]
;  (when (not (= name (first (client-names))))
;    (let [[suffix prefix body] (partition-by #(= % name) (client-names))]
;      (concat prefix body suffix))))
;
;(defn high-score
;  "Get the highest score across the clients."
;  [clients]
;  (apply max (map :score clients)))
;
;(defn valid-card
;  [card client leading-card]
;  (boolean
;    (and
;      (tricks.cards/valid-card card)
;      (in? (:cards client) card)
;      (or
;        (nil? leading-card)
;        (= (second leading-card) (second card))
;        (empty? (filter #(= % (second leading-card)) (map second (:cards client))))))))
