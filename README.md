# Image Load Without Glide And File Knowledge
不用Glide載入圖片, 只給一個jpeg網址, 用urlConnection, 取得byteArray<br/>
透過sampleSize降低圖片的解析度,減少記憶體的使用 //一個100x100的imageView 沒必要塞一張1000x1000的圖檔進去

藉由儲存讀取local圖片 搞懂 internal, external storage(app scope, shared-storage)的差別

![homePage](https://github.com/ohyeah5566/ImageLoadWithoutGlideAndFileKnowledge/blob/master/images/homepage.png)

然後權限跟錯誤處理的部分, 就不特別處理, Android 6~10要自行去給write權限, 然後只要沒網路就會crash

//TODO
一篇有關檔案的文章link

參考:
https://blog.csdn.net/guolin_blog/category_9268670.html
https://blog.csdn.net/guolin_blog/article/details/9316683
