package OBM::Entities::obmDomainRoot;

$VERSION = "1.0";

$debug = 1;

use 5.006_001;
require Exporter;
use strict;

use OBM::Parameters::common;
require OBM::Parameters::ldapConf;
require OBM::Ldap::utils;
use Unicode::MapUTF8 qw(to_utf8 from_utf8 utf8_supported_charset);


sub new {
    my $self = shift;
    my( $links, $deleted ) = @_;

    my %obmDomainRootAttr = (
        type => undef,
        links => undef,
        toDelete => undef,
        domainId => undef,
        domainDesc => undef,
        objectclass => undef,
        dnPrefix => undef,
        dnValue => undef
    );


    if( !defined($links) || !defined($deleted) ) {
        croak( "Usage: PACKAGE->new(LINKS, DELETED)" );

    }

    $obmDomainRootAttr{"links"} = $links;
    $obmDomainRootAttr{"toDelete"} = $deleted;

    $obmDomainRootAttr{"type"} = $OBM::Parameters::ldapConf::DOMAINROOT;

    # Définition de la représentation LDAP de ce type
    $obmDomainRootAttr{objectclass} = $OBM::Parameters::ldapConf::attributeDef->{$obmDomainRootAttr{"type"}}->{objectclass};
    $obmDomainRootAttr{dnPrefix} = $OBM::Parameters::ldapConf::attributeDef->{$obmDomainRootAttr{"type"}}->{dn_prefix};;
    $obmDomainRootAttr{dnValue} = $OBM::Parameters::ldapConf::attributeDef->{$obmDomainRootAttr{"type"}}->{dn_value};

    bless( \%obmDomainRootAttr, $self );
}


sub getEntity {
    my $self = shift;
    my( $domainDesc ) = @_;

    if( !defined($domainDesc->{"domain_id"}) || ($domainDesc->{"domain_id"} !~ /^\d+$/) ) {
        &OBM::toolBox::write_log( "[Entities::obmDomainRoot]: description de domaine OBM incorrecte", "W" );
        return 0;
    }else {
        # On positionne l'identifiant du domaine de l'entité
        $self->{"domainId"} = $domainDesc->{"domain_id"};
    }

    $self->{"domainDesc"} = $domainDesc;

    return 1;
}


sub setDelete {
    my $self = shift;

    $self->{"toDelete"} = 1;

    return 1;
}


sub getDelete {
    my $self = shift;

    return $self->{"toDelete"};
}


sub getArchive {
    my $self = shift;

    return 0;
}


sub getLdapObjectclass {
    my $self = shift;

    return $self->{objectclass};
}


sub getLdapDnPrefix {
    my $self = shift;
    my $dnPrefix = undef;

    if( defined($self->{"dnPrefix"}) && defined($self->{"domainDesc"}->{$self->{"dnValue"}}) ) {
        $dnPrefix = $self->{"dnPrefix"}."=".$self->{"domainDesc"}->{"domain_dn"};
    }

    return $dnPrefix;
}


sub createLdapEntry {
    my $self = shift;
    my( $ldapEntry ) = @_;
    my $entry = $self->{"domainDesc"};


    # On construit la nouvelle entree
    if( $entry->{"domain_label"} ) {
        $ldapEntry->add(
            objectClass => $self->{"objectclass"},
            dc => to_utf8( { -string => $entry->{"domain_dn"}, -charset => $defaultCharSet } ),
            o => to_utf8( { -string => $entry->{"domain_label"}, -charset => $defaultCharSet } )
        );

    }else {
        return 0;
    }

    # La description
    if( $entry->{"domain_desc"} ) {
        $ldapEntry->add( description => to_utf8({ -string => $entry->{"domain_desc"}, -charset => $defaultCharSet }) );
    }

    return 1;
}


sub updateLdapEntryDn {
    my $self = shift;
    my( $ldapEntry ) = @_;
    my $update = 0;


    if( !defined($ldapEntry) ) {
        return 0;
    }


    return $update;
}


sub updateLdapEntry {
    my $self = shift;
    my( $ldapEntry, $objectclassDesc ) = @_;
    my $entry = $self->{"domainDesc"};
    my $update = 0;


    if( !defined($ldapEntry) ) {
        return 0;
    }


    if( &OBM::Ldap::utils::modifyAttr( $entry->{"domain_label"}, $ldapEntry, "o" ) ) {
        $update = 1;
    }

    if( &OBM::Ldap::utils::modifyAttr( $entry->{"domain_desc"}, $ldapEntry, "description" ) ) {
        $update = 1;
    }

    return $update;
}


sub updateLdapEntryLinks {
    my $self = shift;
    my( $ldapEntry ) = @_;
    my $update = 0;


    if( !defined($ldapEntry) ) {
        return 0;
    }


    return $update;
}


sub dump {
    my $self = shift;
    my @desc;

    push( @desc, $self );
    
    require Data::Dumper;
    print Data::Dumper->Dump( \@desc );

    return 1;
}
