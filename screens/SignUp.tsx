import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RouteProp} from '@react-navigation/native';
import {Button, View} from 'react-native';
import {RootStackParamList} from '../lib/types';
import {TextField} from '@/components/common';
import {useState} from 'react';
import {getAuth, createUserWithEmailAndPassword} from 'firebase/auth';
import {getFirestore, collection, doc, setDoc} from 'firebase/firestore';
import {app} from '@/src/firebase/config';

type SignUpProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'SignUp'>;
  route: RouteProp<RootStackParamList, 'SignUp'>;
};

const SignUp: React.FC<SignUpProps> = ({navigation}) => {
  const [formState, setFormState] = useState({
    password: '',
    repeatPassword: '',
    email: '',
    username: '',
  });

  console.log(process.env.G_MEASUREMENT_ID);
  const handleInputChange = (name: string, value: string) => {
    setFormState((prevState) => ({
      ...prevState,
      [name]: value,
    }));
  };

  const auth = getAuth(app);
  const firestore = getFirestore(app);

  const onRegisterPress = () => {
    if (formState.password !== formState.repeatPassword) {
      alert("Passwords don't match.");
      return;
    }
    createUserWithEmailAndPassword(auth, formState.email, formState.password)
      .then((response) => {
        const uid = response.user.uid;
        const data = {
          id: uid,
          email: formState.email,
          fullName: formState.username,
        };
        const usersRef = collection(firestore, 'users');
        setDoc(doc(usersRef, uid), data)
          .then(() => {
            navigation.navigate('DashboardNavigator');
          })
          .catch((error) => {
            alert(error.message);
            console.log('failed firestore');
          });
      })
      .catch((error) => {
        alert(error.message);
      });
  };

  return (
    <View
      style={{
        flex: 1,
        gap: 12,
        paddingHorizontal: 20,
        justifyContent: 'center',
      }}
    >
      <View
        style={{
          gap: 8,
        }}
      >
        <TextField
          value={formState.email}
          onChangeText={(value) => handleInputChange('email', value)}
          placeholder="Email"
        />
        <TextField
          value={formState.username}
          onChangeText={(value) => handleInputChange('username', value)}
          placeholder="Username"
        />
        <TextField
          value={formState.password}
          onChangeText={(value) => handleInputChange('password', value)}
          placeholder="Password"
          secureTextEntry
        />
        <TextField
          value={formState.repeatPassword}
          onChangeText={(value) => handleInputChange('repeatPassword', value)}
          placeholder="Repeat Password"
          secureTextEntry
        />
      </View>
      <Button title="Create An Account" onPress={onRegisterPress} />
    </View>
  );
};
export default SignUp;
