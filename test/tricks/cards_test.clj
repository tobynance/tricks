(ns tricks.cards-test
  (:require [clojure.test :refer :all]
            [tricks.cards :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest create-deck-test
  (testing "Can create correct deck"
    (let [deck (create-deck)]
      (is (= 52 (count deck)))
      (is (= "2H" (nth deck 0)))
      (is (= "AH" (nth deck 12)))
      (is (= "2S" (nth deck 13)))
      (is (= "JD" (nth deck 35)))
      (is (= "AC" (nth deck 51))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest shuffle-deck-test
  (testing "Verify that two calls to shuffle-deck create different permutations of deck."
    (let [deck1 (shuffle-deck (create-deck))
          deck2 (shuffle-deck (create-deck))]
      (is (not (= deck1 deck2))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest face-value-test
  (testing "correctly returns face value"
    (is (= 2 (face-value "2S")))
    (is (= 8 (face-value "8C")))
    (is (= 9 (face-value "9D")))
    (is (= 10 (face-value "TH")))
    (is (= 11 (face-value "JS")))
    (is (= 12 (face-value "QD")))
    (is (= 13 (face-value "KC")))
    (is (= 14 (face-value "AH")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest in-same-suit-test
  (testing "in-same-suit-test"
    (is (in-same-suit "2H" "JH"))
    (is (not (in-same-suit "2S" "JH")))
    (is (in-same-suit "QC" "TC"))
    (is (not (in-same-suit "QC" "QD")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest in?-test
  (testing "in?"
    (is (in? "abc" \c))
    (is (not (in? "abc" \d)))
    (is (in? '(a b c) 'c))
    (is (not (in? '(a b c) 'd)))
    (is (in? [1 2 3] 2))
    (is (not (in? [1 2 3] 8)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest valid-card-test
  (testing "correctly validates cards"
    (is (valid-card "2H"))
    (is (valid-card "TC"))
    (is (valid-card "AS"))
    (is (not (valid-card "SA")))
    (is (not (valid-card "QR")))))
