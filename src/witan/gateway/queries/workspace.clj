(ns witan.gateway.queries.workspace
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]))

(defn dt->str
  [k]
  (fn [e]
    (when-let [time (get e k)]
      (tf/unparse (tf/formatters :basic-date-time) time))))

(def workspace-fields
  [:workspace/name
   :workspace/id
   :workspace/owner-name
   :workspace/owner-id
   :workspace/modified
   :workspace/description])

(def model-fields
  [:metadata
   :workflow
   :catalog])

(def function-fields
  [:function/name
   :function/id
   :function/version])

(defn get-workspaces
  []
  [])

(defn get-workspaces-by-owner
  [u d owner]
  (if (= owner "*")
    (get-workspaces)
    (filter #(= owner (:workspace/owner-id %)) (get-workspaces))))

(defn get-workspace-by-id
  [u d id]
  (some #(when (= id (:workspace/id %)) %) (get-workspaces)))

(defn get-available-models
  [u d]
  [])

(defn get-available-functions
  [u d]
  [])

(defn get-model-by-name-and-version
  [_ name version]
  (some #(when (and (= (:witan/name (:metadata %)) name)
                    (= (:witan/version (:metadata %)) version)) %) (get-available-models)))
