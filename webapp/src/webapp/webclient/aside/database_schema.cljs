(ns webapp.webclient.aside.database-schema
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [webapp.subs :as subs]
   [webapp.webclient.aside.mongodb-schema :as mongodb-schema]
   ["@heroicons/react/20/solid" :as hero-solid-icon]))

(def ^:private get-mysql-schema-query
  " -- Generated by hoop.dev
   SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
   FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'pg_catalog', 'sys')
   ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;")

(def ^:private get-postgres-schema-query
  " -- Generated by hoop.dev
   COPY (SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
   FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'pg_catalog', 'sys')
   ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION)
   TO STDOUT WITH DELIMITER E'\t'CSV HEADER;")

(def ^:private get-sql-server-schema-query
  "-- Generated by hoop.dev
SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE
  FROM
    INFORMATION_SCHEMA.COLUMNS
  WHERE
    TABLE_SCHEMA NOT IN ('information_schema', 'sys', 'mysql', 'msdb', 'tempdb', 'model')
  ORDER BY
    TABLE_SCHEMA,
    TABLE_NAME,
    ORDINAL_POSITION")

(def ^:private get-mysql-schema-with-index-query
  "-- Generated by hoop.dev
   SELECT c.TABLE_SCHEMA, c.TABLE_NAME, s.index_name, s.seq_in_index, s.column_name
   FROM INFORMATION_SCHEMA.COLUMNS c
   INNER JOIN INFORMATION_SCHEMA.statistics s ON c.table_name = s.table_name
   WHERE c.TABLE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'pg_catalog')
   AND c.table_schema = s.table_schema
   ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION;")

(def ^:private get-postgres-schema-with-index-query
  "-- Generated by hoop.dev
   COPY (select schemaname, tablename, indexname, tablespace, indexdef
   from pg_indexes where schemaname = ('public') order by tablename)
   TO STDOUT WITH DELIMITER E'\t'CSV HEADER;")

(def ^:private get-sql-server-schema-with-index-query
  "-- Generated by hoop.dev
SELECT
     TableSchema = c.TABLE_SCHEMA,
     TableName = t.name,
     IndexName = ind.name,
     IndexId = ind.index_id,
     ColumnName = col.name

FROM
     sys.indexes ind
INNER JOIN
     sys.index_columns ic ON  ind.object_id = ic.object_id and ind.index_id = ic.index_id
INNER JOIN
     sys.columns col ON ic.object_id = col.object_id and ic.column_id = col.column_id
INNER JOIN
     sys.tables t ON ind.object_id = t.object_id
INNER JOIN
     INFORMATION_SCHEMA.COLUMNS c ON t.name = c.TABLE_NAME
WHERE
     ind.is_primary_key = 0
     AND ind.is_unique = 0
     AND ind.is_unique_constraint = 0
     AND t.is_ms_shipped = 0
ORDER BY
     c.TABLE_SCHEMA, t.name, ic.key_ordinal;")

(def ^:private get-mongodb-schema-query
  " // Generated by hoop.dev
JSON.stringify(db.getMongo().getDBNames().reduce((acc, current) => {
  const currentCollections = db.getSiblingDB(current).getCollectionNames();
  const collectionsWithFields = currentCollections.reduce((accCollection, currCollection) => {
    accCollection[currCollection] = {};
    return accCollection;
  }, {});
  acc[current] = collectionsWithFields;
  return acc;
}, {}));")

(defn- field-type-tree [type]
  [:div {:class "pl-regular italic"}
   (str "(" type ")")])

(defn- indexes-columns-tree []
  (fn [columns]
    [:div {:class "pl-small"}
     (doall
      (for [[number column] (into (sorted-map) columns)]
        ^{:key (str number column)}
        [:div {:class "flex items-center"}
         [:figure {:class "w-2 flex-shrink-0"}
          [:img {:src "/icons/icon-triangle-right-arrow.svg"}]]
         [:span {:class "px-1"}
          (str number " -")]
         (doall
          (for [[column-name _] column]
            ^{:key column-name}
            [:span
             column-name]))]))]))

(defn- indexes-tree [_]
  (let [dropdown-status (r/atom :closed)]
    (fn [indexes]
      [:div {:class "pl-small"}
       [:div
        [:div {:class "flex items-center gap-small"}
         (if (= @dropdown-status :closed)
           [:> hero-solid-icon/FolderIcon {:class "h-3 w-3 shrink-0 text-white"
                                           :aria-hidden "true"}]
           [:> hero-solid-icon/FolderOpenIcon {:class "h-3 w-3 shrink-0 text-white"
                                               :aria-hidden "true"}])
         [:span {:class (str "hover:underline cursor-pointer "
                             "flex items-center")
                 :on-click #(reset! dropdown-status
                                    (if (= @dropdown-status :open) :closed :open))}
          "Indexes"
          (if (= @dropdown-status :open)
            [:> hero-solid-icon/ChevronUpIcon {:class "h-4 w-4 shrink-0 text-white"
                                               :aria-hidden "true"}]
            [:> hero-solid-icon/ChevronDownIcon {:class "h-4 w-4 shrink-0 text-white"
                                                 :aria-hidden "true"}])]]]
       [:div {:class (when (not= @dropdown-status :open)
                       "h-0 overflow-hidden")}
        (doall
         (for [[index columns-names] indexes]
           ^{:key index}
           [:div {:class "pl-small"}
            [:div {:class "flex items-center gap-small"}
             [:figure {:class "w-3 flex-shrink-0"}
              [:img {:src "/icons/icon-db-column-index.svg"}]]
             [:span {:class "flex items-center"}
              index]]
            [indexes-columns-tree columns-names]]))]])))

(defn- fields-tree [fields]
  (let [dropdown-status (r/atom {})
        dropdown-columns-status (r/atom :closed)]
    (fn []
      [:div {:class "pl-small"}
       [:div
        [:div {:class "flex items-center gap-small"}
         (if (= @dropdown-columns-status :closed)
           [:> hero-solid-icon/FolderIcon {:class "h-3 w-3 shrink-0 text-white"
                                           :aria-hidden "true"}]
           [:> hero-solid-icon/FolderOpenIcon {:class "h-3 w-3 shrink-0 text-white"
                                               :aria-hidden "true"}])
         [:span {:class (str "hover:underline cursor-pointer "
                             "flex items-center")
                 :on-click #(reset! dropdown-columns-status
                                    (if (= @dropdown-columns-status :open) :closed :open))}
          "Columns"
          (if (= @dropdown-columns-status :open)
            [:> hero-solid-icon/ChevronUpIcon {:class "h-4 w-4 shrink-0 text-white"
                                               :aria-hidden "true"}]
            [:> hero-solid-icon/ChevronDownIcon {:class "h-4 w-4 shrink-0 text-white"
                                                 :aria-hidden "true"}])]]]
       [:div {:class (str "pl-small" (when (not= @dropdown-columns-status :open)
                                       " h-0 overflow-hidden"))}
        (doall
         (for [[field field-type] fields]
           ^{:key field}
           [:div
            [:div {:class "flex items-center gap-small"}
             [:> hero-solid-icon/DocumentIcon {:class "h-3 w-3 shrink-0 text-white"
                                               :aria-hidden "true"}]
             [:span {:class (str "hover:text-blue-500 hover:underline cursor-pointer "
                                 "flex items-center")
                     :on-click #(swap! dropdown-status
                                       assoc-in [field]
                                       (if (= (get @dropdown-status field) :open) :closed :open))}
              [:span field]
              (if (= (get @dropdown-status field) :open)
                [:> hero-solid-icon/ChevronUpIcon {:class "h-4 w-4 shrink-0 text-white"
                                                   :aria-hidden "true"}]
                [:> hero-solid-icon/ChevronDownIcon {:class "h-4 w-4 shrink-0 text-white"
                                                     :aria-hidden "true"}])]]
            [:div {:class (when (not= (get @dropdown-status field) :open)
                            "h-0 overflow-hidden")}
             [field-type-tree (first (map key field-type))]]]))]])))

(defn- tables-tree []
  (let [dropdown-status (r/atom {})]
    (fn [tables indexes]
      [:div {:class "pl-small"}
       (doall
        (for [[table fields] tables]
          ^{:key table}
          [:div
           ;; TODO replace this icon with a table icon
           [:div {:class "flex items-center gap-small"}
            [:> hero-solid-icon/TableCellsIcon {:class "h-3 w-3 shrink-0 text-white"
                                                :aria-hidden "true"}]
            [:span {:class (str "hover:text-blue-500 hover:underline cursor-pointer "
                                "flex items-center")
                    :on-click #(swap! dropdown-status
                                      assoc-in [table]
                                      (if (= (get @dropdown-status table) :open) :closed :open))}
             [:span table]
             (if (= (get @dropdown-status table) :open)
               [:> hero-solid-icon/ChevronUpIcon {:class "h-4 w-4 shrink-0 text-white"
                                                  :aria-hidden "true"}]
               [:> hero-solid-icon/ChevronDownIcon {:class "h-4 w-4 shrink-0 text-white"
                                                    :aria-hidden "true"}])]]
           [:div {:class (when (not= (get @dropdown-status table) :open)
                           "h-0 overflow-hidden")}
            [fields-tree (into (sorted-map) fields)]
            [indexes-tree (into (sorted-map) (get indexes table))]]]))])))

(defn- sql-databases-tree [_]
  (let [dropdown-status (r/atom {})]
    (fn [schema indexes]
      [:div.text-xs
       (doall
        (for [[db tables] schema]
          ^{:key db}
          [:div
           [:div {:class "flex items-center gap-small"}
            [:> hero-solid-icon/CircleStackIcon {:class "h-3 w-3 shrink-0 text-white"
                                                 :aria-hidden "true"}]
            [:span {:class (str "hover:text-blue-500 hover:underline cursor-pointer "
                                "flex items-center")
                    :on-click #(swap! dropdown-status
                                      assoc-in [db]
                                      (if (= (get @dropdown-status db) :closed) :open :closed))}
             [:span db]
             (if (not= (get @dropdown-status db) :closed)
               [:> hero-solid-icon/ChevronUpIcon {:class "h-4 w-4 shrink-0 text-white"
                                                  :aria-hidden "true"}]
               [:> hero-solid-icon/ChevronDownIcon {:class "h-4 w-4 shrink-0 text-white"
                                                    :aria-hidden "true"}])]]
           [:div {:class (when (= (get @dropdown-status db) :closed)
                           "h-0 overflow-hidden")}
            [tables-tree (into (sorted-map) tables) (into (sorted-map) (get indexes db))]]]))])))

(defmulti ^:private db-view identity)
(defmethod ^:private db-view :default []
  [:div {:class "text-xs"}
   "Couldn't load the schema"])
(defmethod ^:private db-view "sql-server-csv" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "mssql" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "postgres-csv" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "postgres" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "mysql-csv" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "mysql" [_ schema indexes]
  [sql-databases-tree (into (sorted-map) schema) (into (sorted-map) indexes)])
(defmethod ^:private db-view "mongodb" [_ schema]
  [mongodb-schema/main schema])

(defmulti ^:private tree-view-status identity)
(defmethod ^:private tree-view-status :loading [_]
  [:div
   {:class "flex gap-small items-center py-regular text-xs"}
   [:span {:class "italic"}
    "Loading schema"]
   [:figure {:class "w-3 flex-shrink-0 animate-spin opacity-60"}
    [:img {:src "/icons/icon-loader-circle-white.svg"}]]])
(defmethod ^:private tree-view-status :failure [_ log]
  [:div
   {:class "flex gap-small items-center py-regular text-xs"}
   [:span
    log]])
(defmethod ^:private tree-view-status :default [_ schema indexes connection]
  [db-view (:connection-type connection) schema indexes])

(defmulti get-database-schema identity)
(defmethod get-database-schema "sql-server-csv" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-sql-server-schema-query
                get-sql-server-schema-with-index-query]))
(defmethod get-database-schema "mssql" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-sql-server-schema-query
                get-sql-server-schema-with-index-query]))
(defmethod get-database-schema "postgres-csv" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-postgres-schema-query
                get-postgres-schema-with-index-query]))
(defmethod get-database-schema "postgres" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-postgres-schema-query
                get-postgres-schema-with-index-query]))
(defmethod get-database-schema "mysql-csv" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-mysql-schema-query
                get-mysql-schema-with-index-query]))
(defmethod get-database-schema "mysql" [_ connection]
  (rf/dispatch [:editor-plugin->get-mysql-schema
                connection
                get-mysql-schema-query
                get-mysql-schema-with-index-query]))
(defmethod get-database-schema "mongodb" [_ connection]
  (rf/dispatch [:editor-plugin->get-mongodb-schema
                connection
                get-mongodb-schema-query]))

(defn main [connection]
  (let [database-schema (rf/subscribe [::subs/database-schema])
        local-connection (r/atom (:connection-name connection))
        _ (get-database-schema (:connection-type connection) connection)]
    (fn [{:keys [connection-type connection-name]}]
      (when (not= @local-connection connection-name)
        (let [_ (reset! local-connection connection-name)
              _ (get-database-schema connection-type {:connection-type connection-type
                                                      :connection-name connection-name})]))
      [:div {:class "text-gray-200"}
       [tree-view-status
        (:status (get (:data @database-schema) (:connection-name connection)))
        (:schema-tree (get (:data @database-schema) (:connection-name connection)))
        (:indexes-tree (get (:data @database-schema) (:connection-name connection)))
        connection]])))

