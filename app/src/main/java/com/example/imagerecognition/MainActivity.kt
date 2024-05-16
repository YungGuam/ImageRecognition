package com.example.imagerecognition

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.imagerecognition.data.TfLiteImageClassifier
import com.example.imagerecognition.domain.Classification
import com.example.imagerecognition.presentation.CameraPreview
import com.example.imagerecognition.presentation.ObjectImageAnalyzer
import com.example.imagerecognition.ui.theme.ImageRecognitionTheme
import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.Timestamp
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

@Composable
fun MyApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }
    val firebaseAuth = FirebaseAuth.getInstance()
    var isAdmin by remember { mutableStateOf(false) }
    val userId = firebaseAuth.currentUser?.uid
    if (userId != null) {
        LaunchedEffect(userId) {
            checkIfUserIsAdmin(userId) { result ->
                isAdmin = result
                Log.d(TAG, "isAdmin in CameraView: $isAdmin")
            }
        }
    }
    NavHost(navController = navController, startDestination = "signInPage") {
        composable("cameraView") {
            CameraView(navController, googleSignInClient, firebaseAuth)
        }
        composable("addComment/{classificationId}") { backStackEntry ->
            val classificationId = backStackEntry.arguments?.getString("classificationId")
            if (classificationId != null) {
                AddComment(classificationId, navController, isAdmin, firebaseAuth)
            }
        }
        composable("signInPage") {
            SignInPage(navController, googleSignInClient, firebaseAuth, isAdmin)
        }

    }
}

@Composable
fun CameraView(navController: NavController, googleSignInClient: GoogleSignInClient, firebaseAuth: FirebaseAuth) {
    ImageRecognitionTheme {
        var classifications by remember {
            mutableStateOf(emptyList<Classification>())
        }
        val appContext = LocalContext.current
        val analyzer = remember(appContext){

            ObjectImageAnalyzer(
                classifier = TfLiteImageClassifier(
                    context = appContext
                ),
                onResults = {
                    classifications =
                        it.sortedByDescending { classification -> classification.score }
                            .take(3)
                }
            )
        }
        val controller = remember(appContext){
            LifecycleCameraController(appContext).apply {
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(appContext),
                    analyzer
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            CameraPreview(controller, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .size(321.dp) // Match the size of the cropped area
                    .align(Alignment.Center) // Center the box
                    .border(
                        2.dp,
                        Color.Red,
                        RoundedCornerShape(8.dp)
                    ) // Add a border
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                classifications.forEach {
                    Text(
                        text = "${it.name} - Score: ${it.score}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            SignOutButton(googleSignInClient, firebaseAuth, navController, modifier = Modifier.align(Alignment.BottomEnd))

            // Get the classification with the highest score
            val highestScoreClassification = classifications.maxByOrNull { it.score }

            // Use the name of the classification with the highest score as the classificationId
            val classificationId = highestScoreClassification?.name

            if (classificationId != null) {
                Button(onClick = {
                    navController.navigate("addComment/$classificationId")
                }, modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(text = "Comment")
                }
            }
        }
    }
}

@Composable
fun SignInPage(navController: NavController, googleSignInClient: GoogleSignInClient, firebaseAuth: FirebaseAuth, isAdmin: Boolean) {
    val context = LocalContext.current

    val googleSignInClient = remember {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }

    val resultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d(TAG, "Google sign in successful, account: $account")
                    firebaseAuthWithGoogle(account.idToken!!, navController)
                } catch (e: ApiException) {
                    // Handle sign-in failure
                    Log.e(TAG, "Google sign in failed", e)
                }
            } else {
                Log.d(TAG, "Sign in result not OK")
            }
        }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            val signInIntent = googleSignInClient.signInIntent
            resultLauncher.launch(signInIntent)
        }) {
            Text(text = "Sign in with Google")
        }
    }
    val firebaseAuth = FirebaseAuth.getInstance()
    var authStateListener: FirebaseAuth.AuthStateListener? = null

    LaunchedEffect(Unit) {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d(TAG, "FirebaseAuth user is not null")

                // Navigate to the "cameraView" regardless of the admin status
                navController.navigate("cameraView")
            } else {
                Log.d(TAG, "FirebaseAuth user is null")
            }
        }
        firebaseAuth.addAuthStateListener(authStateListener!!)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (authStateListener != null) {
                firebaseAuth.removeAuthStateListener(authStateListener!!)
            }
        }
    }
}

@Composable
fun AddComment(classificationId: String, navController: NavController, isAdmin: Boolean, firebaseAuth: FirebaseAuth) {
    var comment by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(emptyList<Map<String, Any?>>()) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var editingCommentText by remember { mutableStateOf("") }

    LaunchedEffect(classificationId) {
        getComments(classificationId) { fetchedComments ->
            comments = fetchedComments
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Add a comment", fontSize = 24.sp, modifier = Modifier.padding(16.dp))
        TextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") })
        Button(onClick = {
            addComment(classificationId, comment, comments) { updatedComments ->
                comments = updatedComments
            }
        }) {
            Text(text = "Submit")
        }

        LazyColumn {
            items(comments) { comment ->
                val username = comment["username"] as? String ?: "Unknown"
                val timestamp = (comment["timestamp"] as Timestamp).toDate().toString()

                Text(text = "User: $username")
                Text(text = "Time: $timestamp")

                Log.d(TAG, "isAdmin in AddComment: $isAdmin")
                Log.d(TAG, "comment id: ${comment["id"]}")
                if (firebaseAuth.currentUser!!.uid == comment["userId"]) {
                    if (editingCommentId == comment["id"]) {
                        TextField(value = editingCommentText, onValueChange = { editingCommentText = it })
                        Button(onClick = {
                            updateComment(editingCommentId!!, editingCommentText, comments) { updatedComments ->
                                comments = updatedComments
                            }
                            editingCommentId = null
                        }) {
                            Text("Save")
                        }
                    } else {
                        Text(text = "Comment: ${comment["comment"]}")
                        Button(onClick = {
                            editingCommentId = comment["id"] as String
                            editingCommentText = comment["comment"] as String
                        }) {
                            Text("Edit")
                        }
                    }
                } else {
                    Text(text = "Comment: ${comment["comment"]}")
                }
                if (isAdmin) {
                    Button(onClick = {
                        deleteComment(comment["id"] as String, comments) { updatedComments ->
                            comments = updatedComments
                        }
                    }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}


fun addComment(classificationId: String, comment: String, comments: List<Map<String, Any?>>, onCommentsUpdated: (List<Map<String, Any?>>) -> Unit) {
    val firebaseAuth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val commentData = hashMapOf(
        "classificationId" to classificationId,
        "username" to firebaseAuth.currentUser!!.displayName,
        "userId" to firebaseAuth.currentUser!!.uid, // Add the userId to the comment data
        "comment" to comment,
        "timestamp" to Timestamp.now()
    )

    db.collection("comments")
        .add(commentData)
        .addOnSuccessListener { documentReference ->
            Log.d(TAG, "Comment successfully written!")
            // Add the new comment to the comments list and update the state
            val newComment = commentData.apply { this["id"] = documentReference.id }
            onCommentsUpdated(comments + newComment)
        }
        .addOnFailureListener { e -> Log.w(TAG, "Error writing comment", e) }
}

fun getComments(classificationId: String, onResult: (List<Map<String, Any>>) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    db.collection("comments")
        .whereEqualTo("classificationId", classificationId)
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .get()
        .addOnSuccessListener { documents ->
            Log.d(TAG, "Successfully fetched ${documents.size()} comments for classificationId $classificationId")
            onResult(documents.map { document ->
                val data = document.data
                data["id"] = document.id // Add the document ID to the data
                data
            })
        }
        .addOnFailureListener { exception ->
            Log.w(TAG, "Error getting comments for classificationId $classificationId: ", exception)
        }
}

fun deleteComment(commentId: String, comments: List<Map<String, Any?>>, onCommentsUpdated: (List<Map<String, Any?>>) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    db.collection("comments")
        .document(commentId)
        .delete()
        .addOnSuccessListener {
            Log.d(TAG, "Comment successfully deleted!")
            onCommentsUpdated(comments.filter { it["id"] != commentId })
        }
        .addOnFailureListener { e -> Log.w(TAG, "Error deleting comment", e) }
}

fun updateComment(commentId: String, newCommentText: String, comments: List<Map<String, Any?>>, onCommentsUpdated: (List<Map<String, Any?>>) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    db.collection("comments")
        .document(commentId)
        .update("comment", newCommentText)
        .addOnSuccessListener {
            Log.d(TAG, "Comment successfully updated!")
            val updatedComments = comments.map { comment ->
                if (comment["id"] == commentId) {
                    comment.toMutableMap().apply { this["comment"] = newCommentText }
                } else {
                    comment
                }
            }
            onCommentsUpdated(updatedComments)
        }
        .addOnFailureListener { e -> Log.w(TAG, "Error updating comment", e) }
}
@Composable
fun SignOutButton(googleSignInClient: GoogleSignInClient, firebaseAuth: FirebaseAuth, navController: NavController, modifier: Modifier = Modifier) {
    Button(onClick = {
        firebaseAuth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            navController.navigate("signInPage")
        }
    }, modifier = modifier) {
        Text(text = "Sign Out")
    }
}
fun firebaseAuthWithGoogle(idToken: String, navController: NavController) {
    val firebaseAuth = FirebaseAuth.getInstance()
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    firebaseAuth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Sign in success
                Log.d(TAG, "signInWithCredential:success")
                val user = firebaseAuth.currentUser
                if (user != null) {
                    createUserDocument(user.uid, user.displayName ?: "", user.email ?: "")
                }
                navController.navigate("cameraView")
            } else {
                // If sign in fails, display a message to the user.
                Log.w(TAG, "signInWithCredential:failure", task.exception)
            }
        }
}

fun createUserDocument(userId: String, displayName: String, email: String) {
    val db = FirebaseFirestore.getInstance()
    val userDocument = hashMapOf(
        "userId" to userId, // Add the "userId" field
        "displayName" to displayName,
        "email" to email,
        "role" to "user" // Add the "role" field
        // Add other user information as needed
    )
    db.collection("users")
        .document(userId)
        .get()
        .addOnSuccessListener { document ->
            if (!document.exists()) {
                db.collection("users")
                    .document(userId)
                    .set(userDocument)
                    .addOnSuccessListener { Log.d(TAG, "User document successfully written!") }
                    .addOnFailureListener { e -> Log.w(TAG, "Error writing user document", e) }
            }
        }
        .addOnFailureListener { e -> Log.w(TAG, "Error checking user document", e) }
}
fun checkIfUserIsAdmin(userId: String, onResult: (Boolean) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    db.collection("users")
        .document(userId)
        .get()
        .addOnSuccessListener { document ->
            val isAdmin = document.exists() && document.getString("role") == "admin"
            Log.d(TAG, "User is admin: $isAdmin")
            onResult(isAdmin)
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Error checking admin status", e)
            onResult(false)
        }
}
private fun hasCameraPermission(context: Context) = ContextCompat.checkSelfPermission(
    context, Manifest.permission.CAMERA
) == PackageManager.PERMISSION_GRANTED

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        // for other device how are able to connect with Ethernet
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        // for check internet over Bluetooth
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
        else -> false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasCameraPermission(this)) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 0
            )
        }
        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
            // Here you can navigate to an error screen or show an error dialog
        }
        setContent {
            MyApp()
        }
    }
}
