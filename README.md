# Keymaker
secure password vault

This application generates pseudorandom passwords associated to account names. It uses a master password to generate a seed with which these passwords are built. 
The application tries to maximize efficient usability and security. 
usability: Enter an account name and press enter to copy the generated password directly to the clipboard.
security: No password is ever stored, only its meta information: account name, creation timestamp and build format. 
To re-use a previous password, the application will re-generate it with this information.

The master password can be verified. However, a this verification is only a hint that the master password is correct. 
This is because the master password is not saved by itself, but only by an indicative number generated from it. 
It is impossible to deterministically revert this indicative number back into the master password or the master password's hash (which is used to generate the passwords).
