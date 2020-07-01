import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutterapp/imagenet.dart';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' show join;
import 'package:path_provider/path_provider.dart';

Future<void> main() async {
  // Ensure that plugin services are initialized so that `availableCameras()`
  // can be called before `runApp()`
  WidgetsFlutterBinding.ensureInitialized();

  // Obtain a list of the available cameras on the device.
  final cameras = await availableCameras();

  // Get a specific camera from the list of available cameras.
  final firstCamera = cameras.first;

  runApp(
    MaterialApp(
      theme: ThemeData.dark(),
      home: TakePictureScreen(
        // Pass the appropriate camera to the TakePictureScreen widget.
        camera: firstCamera,
      ),
    ),
  );
}

// A screen that allows users to take a picture using a given camera.
class TakePictureScreen extends StatefulWidget {
  final CameraDescription camera;

  const TakePictureScreen({
    Key key,
    @required this.camera,
  }) : super(key: key);

  @override
  TakePictureScreenState createState() => TakePictureScreenState();
}

class TakePictureScreenState extends State<TakePictureScreen> {
  CameraController _controller;
  String tmpPath;
  int frames = 0;
  Future<void> _initializeControllerFuture;
  static const platform = const MethodChannel('samples.flutter.dev/battery');
  int frameCounter = 0;
  int _prediction = 0;
  String error;
  bool busy = false;

  Future<void> _getPrediction(CameraImage cameraImage) async {
//    print("kurwaaa");
    var framesY = cameraImage.planes[0].bytes;
    var framesU = cameraImage.planes[1].bytes;
    var framesV = cameraImage.planes[2].bytes;
    bool res = false;
    try {
      res = await platform.invokeMethod('getPrediction',
          <String, dynamic>{'width':cameraImage.width, 'height':cameraImage.height,
                            'Y': framesY,'U': framesU,'V': framesV});
      //await platform.invokeMethod('getPrediction', {'file': file});
    } on PlatformException catch (e) {
      print("kurwaaa213 error: ${e.message}");
      error = e.message;
    }

    setState(() {
      busy = res;
    });
  }

  @override
  void initState() {
    super.initState();
    // To display the current output from the Camera,
    // create a CameraController.
    _controller = CameraController(
      // Get a specific camera from the list of available cameras.
      widget.camera,
      // Define the resolution to use.
      ResolutionPreset.max,
    );
    // Next, initialize the controller. This returns a Future.
    _initializeControllerFuture = _controller.initialize();

    platform.setMethodCallHandler((MethodCall call) async{
      if(call.method == "predictionResult"){
        final args = call.arguments;
        setState(() {
          _prediction = args["result"];
          busy = false;
        });
      }
      return true;
    });
    getTemporaryDirectory().then((onValue) {
      tmpPath = "${onValue.path}/xd.png";
    });

    _initializeControllerFuture.then( (x) {

      _controller.startImageStream((CameraImage availableImage) async {
        if(busy || frames++ < 60)
            return;
        frames = 0;
        //await _controller.takePicture(tmpPath);
        await _getPrediction(availableImage);
      });

    }
    );

  }

  @override
  void dispose() {
    // Dispose of the controller when the widget is disposed.
    _controller.stopImageStream();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    List<String> money =["10", "20", "50", "100", "200", "500", "None"];
    return Scaffold(
      appBar: AppBar(title: Text('${money[_prediction]}')),
      // Wait until the controller is initialized before displaying the
      // camera preview. Use a FutureBuilder to display a loading spinner
      // until the controller has finished initializing.
      body: FutureBuilder<void>(
        future: _initializeControllerFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.done) {
            // If the Future is complete, display the preview.
            return CameraPreview(_controller);
          } else {
            // Otherwise, display a loading indicator.
            return Center(child: CircularProgressIndicator());
          }
        },
      ),
      floatingActionButton: FloatingActionButton(
        child: Icon(Icons.camera_alt),
        // Provide an onPressed callback.
        //onPressed: _getPrediction,
      ),
    );
  }
}

// A widget that displays the picture taken by the user.
class DisplayPictureScreen extends StatelessWidget {
  final String imagePath;

  const DisplayPictureScreen({Key key, this.imagePath}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Display the Picture')),
      // The image is stored as a file on the device. Use the `Image.file`
      // constructor with the given path to display the image.
      body: Image.file(File(imagePath)),
    );
  }
}
