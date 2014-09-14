(ns tricks.utils-test
  (:require [clojure.test :refer :all]
            [tricks.utils :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest in?-test
  (testing "in?"
    (is (in? "abc" \c))
    (is (not (in? "abc" \d)))
    (is (in? '(a b c) 'c))
    (is (not (in? '(a b c) 'd)))
    (is (in? [1 2 3] 2))
    (is (not (in? [1 2 3] 8)))))
