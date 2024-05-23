(ns com.github.didibus.simple-website-with-posts
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.form :refer [form-to text-field submit-button text-area]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [honey.sql :as hsql]
            [honey.sql.helpers :refer [select from where insert-into values]]
            [ring.util.response :refer [redirect]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(def db {:dbtype "h2:mem" :dbname "simple-website-with-posts"})

(def ds (jdbc/with-options
          (jdbc/get-datasource db)
          {:builder-fn rs/as-unqualified-lower-maps}))

(defn create-tables []
  (jdbc/execute! ds ["
CREATE TABLE IF NOT EXISTS posts (
  id IDENTITY,
  title VARCHAR(255),
  content TEXT
)"]))

(create-tables)

(defn create-post-form []
  [:div {:class "form-container"}
   [:h2 "Create a new Post"]
   (form-to [:post "/create-post"]
            (anti-forgery-field)
            [:div {:class "form-group"}
             [:label {:for "title"} "Title"]
             (text-field {:class "form-control" :id "title"} "title")]
            [:div {:class "form-group"}
             [:label {:for "content"} "Content"]
             (text-area {:class "form-control" :id "content"} "content")]
            (submit-button {:class "btn btn-primary"} "Create Post"))])

(defn home-page []
  (let [query (hsql/format (-> (select :*)
                               (from :posts)))
        posts (jdbc/execute! ds query)]
    (html5
        [:head
         [:title "Posts"]
         (include-css "/styles.css")]
      [:body
       [:div {:class "container"}
        [:h1 "All Posts"]
        [:div {:class "post-list"}
         (for [post posts]
           [:a {:href (str "/post/" (:id post))} (:title post)])]
        (create-post-form)]])))

(defn post-page [id]
  (let [query (hsql/format (-> (select :*)
                               (from :posts)
                               (where [:= :id id])))
        post (jdbc/execute-one! ds query)]
    (html5
        [:head
         [:title (:title post)]
         (include-css "/styles.css")]
      [:body
       [:div {:class "container"}
        [:h1 (:title post)]
        [:p (:content post)]]])))

(defn save-post [title content]
  (let [query (hsql/format (-> (insert-into :posts)
                               (values [{:title title :content content}])))]
    (jdbc/execute! ds query)))

(defroutes app
  (GET "/" [] (home-page))
  (GET "/post/:id" [id] (post-page id))
  (POST "/create-post" [title content]
    (save-post title content)
    (redirect "/"))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app-with-middleware
  (-> app
      (wrap-resource "public")
      (wrap-defaults site-defaults)))

(defn -main []
  (run-jetty #'app-with-middleware {:port 3000}))
