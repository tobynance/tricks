(ns clojure-client.core-test
  (:require [clojure.test :refer :all]
            [clojure-client.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest send-card-test
  (testing "sends lowest card if cannot win"
    (let [client-state {:cards ["2S" "9S" "7S"] :played ["JS" "KS"]}]
      (is (= {:cards ["9S" "7S"] :played ["JS" "KS"]} (send-card client-state)))))

  (testing "sends lowest card if cannot win (out of order)"
    (let [client-state {:cards ["9S" "7S"] :played ["JS" "KS"]}]
      (is (= {:cards ["9S"] :played ["JS" "KS"]} (send-card client-state)))))

  (testing "sends lowest card if cannot win (other suits present)"
    (let [client-state {:cards ["3D" "7H" "9S" "6C" "8C" "JD" "4H" "8H" "TD" "3H" "7S"] :played ["JS" "KS"]}]
      (is (= {:cards ["3D" "7H" "9S" "6C" "8C" "JD" "4H" "8H" "TD" "3H"] :played ["JS" "KS"]} (send-card client-state)))))

  (testing "sends lowest card out of suit if no card in suit available"
    (let [client-state {:cards ["3D" "7H" "6C" "8C" "JD" "4H" "8H" "TD" "3H"] :played ["JS" "KS"]}]
      (is (= {:cards ["7H" "6C" "8C" "JD" "4H" "8H" "TD" "3H"] :played ["JS" "KS"]} (send-card client-state)))))

  (testing "sends higher card if can win"
    (let [client-state {:cards ["2S" "9S" "AS" "7S"] :played ["JS" "KS"]}]
      (is (= {:cards ["2S" "9S" "7S"] :played ["JS" "KS"]} (send-card client-state)))))

  (testing "sends highest card needed to win, and no higher"
    (let [client-state {:cards ["2S" "9S" "AS" "7S"] :played ["8S" "6S"]}]
      (is (= {:cards ["2S" "AS" "7S"] :played ["8S" "6S"]} (send-card client-state)))))

  (testing "Can handle someone else playing out of suit"
    (let [client-state {:cards ["9D" "6S" "JH" "QD" "8D" "QH" "7S" "7H"] :played ["2D" "TD" "QS"]}]
      (is (= {:cards ["9D" "6S" "JH" "8D" "QH" "7S" "7H"] :played ["2D" "TD" "QS"]} (send-card client-state)))))

  )
