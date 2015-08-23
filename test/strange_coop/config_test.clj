(ns strange-coop.config-test
  (:require [clojure.test :refer :all]
            [strange-coop.components.config :refer :all]))

(deftest deep-merge-in-test
  (testing "when there is already a map"
    (is (= (deep-merge-in {:this {:that {:more :stuff}}} [:this] {:that {:what :not}} {:gotta :haveit})
           {:this {:that {:more :stuff, :what :not}, :gotta :haveit}})))
  (testing "when there is no map there already"
    (is (= (deep-merge-in {} [:this] {:that {:what :not}} {:gotta :haveit})
           {:this {:that {:what :not} :gotta :haveit}}))))

