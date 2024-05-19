import {initializeApp} from 'firebase/app';
import {initializeAuth, getReactNativePersistence} from 'firebase/auth';
import {getFirestore} from 'firebase/firestore';
import AsyncStorage from '@react-native-async-storage/async-storage';

const firebaseConfig = {
  apiKey: '***REMOVED***',

  authDomain: '***REMOVED******REMOVED***',

***REMOVED***

***REMOVED***

***REMOVED***

  appId: '1:***REMOVED******REMOVED***',

***REMOVED***
};

const app = initializeApp(firebaseConfig);

const auth = initializeAuth(app, {
  persistence: getReactNativePersistence(AsyncStorage),
});

const firestore = getFirestore(app);

export {app, auth, firestore};
