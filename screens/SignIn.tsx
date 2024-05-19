import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RouteProp} from '@react-navigation/native';
import {Button, View, Text} from 'react-native';
import {RootStackParamList} from '../lib/types';
import {TextField} from '@/components/common';

type SignInProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'SignIn'>;
  route: RouteProp<RootStackParamList, 'SignIn'>;
};

const SignIn: React.FC<SignInProps> = ({navigation}) => {
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
        <TextField placeholder="Email" />
        <TextField placeholder="Password" />
      </View>
      <Button
        title="Sign In"
        // onPress={() => navigation.navigate('DashboardNavigator')}
      />
      <Button title="Sign Up" onPress={() => navigation.navigate('SignUp')} />
    </View>
  );
};
export default SignIn;
