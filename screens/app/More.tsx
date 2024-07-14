import React from 'react';
import {View, Button} from 'react-native';
import {AppTabParamList, RootStackParamList} from '@/lib/types';
import {BottomTabScreenProps} from '@react-navigation/bottom-tabs';
import {getAuth, signOut} from 'firebase/auth';
import {useAppDispatch, useAppSelector} from '@/hooks/redux';
import {setUser} from '@/store/user.slice';

type MoreProps = BottomTabScreenProps<
  AppTabParamList & RootStackParamList,
  'More'
>;

const More: React.FC<MoreProps> = ({navigation}) => {
  const user = useAppSelector((state) => state.user.user);
  console.log('more/user:', user);
  const dispatch = useAppDispatch();
  const temporarySignOut = () => {
    const auth = getAuth();
    signOut(auth)
      .then(() => {
        console.log('nara');
        dispatch(setUser(''));
        navigation.navigate('SignIn');
      })
      .catch((error) => {
        console.log(error);
      });
  };
  return (
    <View style={{flex: 1, justifyContent: 'center'}}>
      <Button title="Sign Out" onPress={temporarySignOut} />
    </View>
  );
};

export default More;
