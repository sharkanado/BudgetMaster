import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RouteProp} from '@react-navigation/native';
import {Button, View, Text} from 'react-native';
import {RootStackParamList} from '../lib/types';
import {TextField} from '@/components/common';
import {useState} from 'react';
import {getAuth, signInWithEmailAndPassword} from 'firebase/auth';
import {setUser} from '@/store/user.slice';
import {useAppDispatch, useAppSelector} from '@/hooks/redux';

type SignInProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'SignIn'>;
  route: RouteProp<RootStackParamList, 'SignIn'>;
};

const SignIn: React.FC<SignInProps> = ({navigation}) => {
  const dispatch = useAppDispatch();
  const userState = useAppSelector((state) => state.user.user);
  const [formState, setFormState] = useState({
    password: '',
    email: '',
  });

  console.log('currentUser:', userState);
  const handleInputChange = (name: string, value: string) => {
    setFormState((prevState) => ({
      ...prevState,
      [name]: value,
    }));
  };

  const onSignInPress = async () => {
    const auth = getAuth();
    try {
      const userCredential = await signInWithEmailAndPassword(
        auth,
        formState.email,
        formState.password
      );
      const user = userCredential.user;
      dispatch(setUser(user.email as string));
      console.log('user: ', user);
      if (user) navigation.navigate('DashboardNavigator');
    } catch (error) {
      console.log(error);
      // Optionally, handle the error by showing an alert or some feedback to the user
    }
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
      <Text style={{textAlign: 'center', fontWeight: 'bold', fontSize: 30}}>
        BudgetMaster
      </Text>
      <View
        style={{
          gap: 8,
        }}
      >
        <TextField
          value={formState.email}
          placeholder="Email"
          onChangeText={(value) => handleInputChange('email', value)}
        />
        <TextField
          value={formState.password}
          placeholder="Password"
          onChangeText={(value) => handleInputChange('password', value)}
        />
      </View>
      <Button title="Sign In" onPress={onSignInPress} />
      <Button title="Sign Up" onPress={() => navigation.navigate('SignUp')} />
    </View>
  );
};
export default SignIn;
