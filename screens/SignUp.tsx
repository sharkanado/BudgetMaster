import {NativeStackNavigationProp} from '@react-navigation/native-stack';
import {RouteProp} from '@react-navigation/native';
import {Button, View} from 'react-native';
import {RootStackParamList} from '../lib/types';
import {TextField} from '@/components/common';

type SignUpProps = {
  navigation: NativeStackNavigationProp<RootStackParamList, 'SignUp'>;
  route: RouteProp<RootStackParamList, 'SignUp'>;
};

const SignUp: React.FC<SignUpProps> = ({navigation}) => {
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
        <TextField placeholder="Email" />
        <TextField placeholder="Username" />
        <TextField placeholder="Password" />
        <TextField placeholder="Repeat Password" />
      </View>
      <Button
        title="Create An Account"
        onPress={() => navigation.navigate('DashboardNavigator')}
      />
    </View>
  );
};
export default SignUp;
